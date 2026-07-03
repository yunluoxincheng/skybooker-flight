package com.skybooker.ai.parser;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IntentParserService implements IntentParser {

    private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
            Map.entry("上海", "上海"), Map.entry("北京", "北京"), Map.entry("广州", "广州"),
            Map.entry("深圳", "深圳"), Map.entry("成都", "成都"), Map.entry("杭州", "杭州"),
            Map.entry("重庆", "重庆"), Map.entry("武汉", "武汉"), Map.entry("西安", "西安"),
            Map.entry("南京", "南京"), Map.entry("长沙", "长沙"), Map.entry("青岛", "青岛"),
            Map.entry("大连", "大连"), Map.entry("厦门", "厦门"), Map.entry("昆明", "昆明"),
            Map.entry("三亚", "三亚"), Map.entry("海口", "海口"), Map.entry("哈尔滨", "哈尔滨"),
            Map.entry("沈阳", "沈阳"), Map.entry("天津", "天津"), Map.entry("郑州", "郑州"),
            Map.entry("福州", "福州"), Map.entry("贵阳", "贵阳"), Map.entry("南宁", "南宁"),
            Map.entry("兰州", "兰州"), Map.entry("太原", "太原"), Map.entry("合肥", "合肥"),
            Map.entry("济南", "济南"), Map.entry("南昌", "南昌"), Map.entry("长春", "长春"),
            Map.entry("乌鲁木齐", "乌鲁木齐"), Map.entry("呼和浩特", "呼和浩特"),
            Map.entry("拉萨", "拉萨"), Map.entry("银川", "银川"), Map.entry("西宁", "西宁"),
            Map.entry("石家庄", "石家庄")
    );

    private static final List<String[]> AIRLINE_PATTERNS = List.of(
            // short names -> full name
            new String[]{"南航", "南方航空"},
            new String[]{"国航", "中国国航"},
            new String[]{"东航", "东方航空"},
            new String[]{"海航", "海南航空"},
            new String[]{"春秋", "春秋航空"},
            new String[]{"吉祥", "吉祥航空"},
            new String[]{"厦航", "厦门航空"},
            new String[]{"川航", "四川航空"},
            new String[]{"山航", "山东航空"},
            // full names (match as-is)
            new String[]{"南方航空", "南方航空"},
            new String[]{"中国国航", "中国国航"},
            new String[]{"中国国际航空", "中国国航"},
            new String[]{"东方航空", "东方航空"},
            new String[]{"海南航空", "海南航空"},
            new String[]{"春秋航空", "春秋航空"},
            new String[]{"吉祥航空", "吉祥航空"},
            new String[]{"厦门航空", "厦门航空"},
            new String[]{"四川航空", "四川航空"},
            new String[]{"山东航空", "山东航空"},
            // two-letter codes
            new String[]{"MU", "东方航空"},
            new String[]{"CZ", "南方航空"},
            new String[]{"CA", "中国国航"},
            new String[]{"HU", "海南航空"}
    );

    private static final Map<String, String> CABIN_MAP = Map.of(
            "经济舱", "ECONOMY", "头等舱", "FIRST", "商务舱", "BUSINESS",
            "公务舱", "BUSINESS"
    );

    private static final Pattern ROUTE_FROM_TO = Pattern.compile(
            "从(.+?)[飞到去](.+?)(?=[，。,.\\s\\d明今后下晚早]|$)");
    private static final Pattern ROUTE_SIMPLE = Pattern.compile(
            "(.+?)[→\\->](.+?)");
    private static final Pattern DESTINATION_ONLY = Pattern.compile(
            "(?:我想|想|我要|帮我)?(?:去|飞往|飞)([\\u4e00-\\u9fa5]{2,8})");
    private static final Pattern DEPARTURE_CITY_ONLY = Pattern.compile(
            "(?:从|自)([\\u4e00-\\u9fa5]{2,8})(?:出发|走|起飞)");
    private static final Pattern ARRIVAL_CITY_ONLY = Pattern.compile(
            "(?:目的地|到达|飞往|去)([\\u4e00-\\u9fa5]{2,8})");
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})[日号]?");
    private static final Pattern SHORT_DATE = Pattern.compile(
            "(\\d{1,2})[月/-](\\d{1,2})[日号]?");
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(\\d{1,2}[月/-]\\d{1,2}[日号]?|\\d{4}[年/-]\\d{1,2}[月/-]\\d{1,2}[日号]?|(?:下|这|本)?(?:周|星期)[一二三四五六日天])\\s*(?:到|至|-|~|—)\\s*(\\d{1,2}[月/-]\\d{1,2}[日号]?|\\d{4}[年/-]\\d{1,2}[月/-]\\d{1,2}[日号]?|(?:下|这|本)?(?:周|星期)[一二三四五六日天])");
    private static final Pattern MULTI_WEEKDAY = Pattern.compile(
            "(?:周|星期)[一二三四五六日天].*(?:周|星期)[一二三四五六日天].*(?:都可以|均可|都行|任选)");
    private static final Pattern PASSENGER_PATTERN = Pattern.compile(
            "(?<![0-9-])(\\d{1,2})(?:个?人|位)(?!\\d)");
    private static final Pattern PASSENGER_CN_PATTERN = Pattern.compile(
            "([一二两三四五六七八九十])(?:个?人|位)");
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?:低于|不超过|最高|最多|预算)[^\\d]?(\\d+)");
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:不超过|最多|短于|小于)[^\\d]?(\\d+)(小时|分钟|min)");

    private Clock clock = Clock.systemDefaultZone();

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ParsedCondition parse(String message) {
        if (message == null || message.isBlank()) {
            return ParsedCondition.builder()
                    .missingFields(List.of("departureCity", "arrivalCity", "departureDate"))
                    .followUpQuestion("请问您想从哪个城市出发，飞往哪个城市，什么时间出发？")
                    .quickActionLabels(List.of())
                    .build();
        }

        String text = message.trim();
        boolean ambiguousDateExpression = hasAmbiguousDateExpression(text);

        String departureCity = null;
        String arrivalCity = null;
        LocalDate departureDate = null;
        Integer passengerCount = null;
        String cabinClass = null;
        String airlineRaw = null;
        BigDecimal maxPrice = null;
        LocalTime departureTimeStart = null;
        LocalTime departureTimeEnd = null;
        Integer maxDurationMinutes = null;
        Boolean directOnly = null;
        String sort = null;

        // Route parsing
        Matcher routeFromTo = ROUTE_FROM_TO.matcher(text);
        if (routeFromTo.find()) {
            departureCity = resolveCity(routeFromTo.group(1).trim());
            arrivalCity = resolveCity(routeFromTo.group(2).trim());
        }

        if (departureCity == null) {
            Matcher routeSimple = ROUTE_SIMPLE.matcher(text);
            if (routeSimple.find()) {
                departureCity = resolveCity(routeSimple.group(1).trim());
                arrivalCity = resolveCity(routeSimple.group(2).trim());
            }
        }

        if (departureCity == null) {
            String[] route = parseKnownCityRoute(text);
            if (route != null) {
                departureCity = route[0];
                arrivalCity = route[1];
            }
        }

        if (arrivalCity == null) {
            Matcher destinationOnly = DESTINATION_ONLY.matcher(text);
            if (destinationOnly.matches()) {
                arrivalCity = resolveCity(destinationOnly.group(1).trim());
            }
        }

        if (departureCity == null) {
            Matcher departureOnly = DEPARTURE_CITY_ONLY.matcher(text);
            if (departureOnly.find()) {
                departureCity = resolveCity(departureOnly.group(1).trim());
            }
        }

        if (arrivalCity == null) {
            Matcher arrivalOnly = ARRIVAL_CITY_ONLY.matcher(text);
            if (arrivalOnly.find()) {
                arrivalCity = resolveCity(arrivalOnly.group(1).trim());
            }
        }

        // Date parsing - explicit first
        if (!ambiguousDateExpression) {
            Matcher dateMatcher = DATE_PATTERN.matcher(text);
            if (dateMatcher.find()) {
                int year = Integer.parseInt(dateMatcher.group(1));
                int month = Integer.parseInt(dateMatcher.group(2));
                int day = Integer.parseInt(dateMatcher.group(3));
                departureDate = safeDate(year, month, day);
            }

            if (departureDate == null) {
                Matcher shortDate = SHORT_DATE.matcher(text);
                if (shortDate.find()) {
                    int month = Integer.parseInt(shortDate.group(1));
                    int day = Integer.parseInt(shortDate.group(2));
                    departureDate = resolveMonthDay(month, day);
                }
            }

            // Relative dates
            if (departureDate == null) {
                departureDate = parseRelativeDate(text);
            }
        }

        // Passenger count
        Matcher passengerMatcher = PASSENGER_PATTERN.matcher(text);
        if (passengerMatcher.find()) {
            passengerCount = Integer.parseInt(passengerMatcher.group(1));
        } else {
            Matcher passengerCnMatcher = PASSENGER_CN_PATTERN.matcher(text);
            if (passengerCnMatcher.find()) {
                passengerCount = parseChinesePassengerCount(passengerCnMatcher.group(1));
            }
        }

        // Cabin class
        for (Map.Entry<String, String> entry : CABIN_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                cabinClass = entry.getValue();
                break;
            }
        }

        // Airline — match short names, full names, then codes
        for (String[] pattern : AIRLINE_PATTERNS) {
            if (text.contains(pattern[0])) {
                airlineRaw = pattern[1];
                break;
            }
        }

        // Price preference
        if (text.contains("便宜") || text.contains("低价") || text.contains("特价") || text.contains("性价比")) {
            sort = "PRICE_ASC";
        }
        Matcher priceMatcher = PRICE_PATTERN.matcher(text);
        if (priceMatcher.find()) {
            maxPrice = new BigDecimal(priceMatcher.group(1));
        }

        // Duration preference
        if (text.contains("最快") || text.contains("最短") || text.contains("时间短")) {
            sort = "DURATION_ASC";
        }
        Matcher durationMatcher = DURATION_PATTERN.matcher(text);
        if (durationMatcher.find()) {
            int value = Integer.parseInt(durationMatcher.group(1));
            String unit = durationMatcher.group(2);
            if ("分钟".equals(unit) || "min".equals(unit)) {
                maxDurationMinutes = value;
            } else {
                maxDurationMinutes = value * 60;
            }
        }

        // Time-of-day preference
        if (text.contains("早上") || text.contains("早班")) {
            departureTimeStart = LocalTime.of(6, 0);
            departureTimeEnd = LocalTime.of(12, 0);
        } else if (text.contains("下午")) {
            departureTimeStart = LocalTime.of(12, 0);
            departureTimeEnd = LocalTime.of(18, 0);
        } else if (text.contains("晚上") || text.contains("晚班") || text.contains("夜间")) {
            departureTimeStart = LocalTime.of(18, 0);
            departureTimeEnd = LocalTime.of(23, 59);
        } else if (text.contains("上午")) {
            departureTimeStart = LocalTime.of(8, 0);
            departureTimeEnd = LocalTime.of(12, 0);
        }

        // Direct flight
        if (text.contains("直飞") || text.contains("直达") || text.contains("不经停") || text.contains("直航")) {
            directOnly = true;
        }

        // Sort hints
        if (sort == null) {
            if (text.contains("准点") || text.contains("准时")) {
                sort = "PUNCTUAL_DESC";
            }
        }

        // Build result
        List<String> missingFields = new ArrayList<>();
        if (departureCity == null) missingFields.add("departureCity");
        if (arrivalCity == null) missingFields.add("arrivalCity");
        if (departureDate == null) missingFields.add("departureDate");

        boolean complete = missingFields.isEmpty();

        String followUpQuestion = null;
        List<String> quickActionLabels = List.of();

        if (!complete) {
            followUpQuestion = ambiguousDateExpression && missingFields.contains("departureDate")
                    ? "目前一次只能查询一个出发日期，请您选择一个具体日期。"
                    : buildFollowUpQuestion(missingFields);
            quickActionLabels = buildQuickActions(missingFields, departureCity, arrivalCity);
        }

        return ParsedCondition.builder()
                .departureCity(departureCity)
                .arrivalCity(arrivalCity)
                .departureDate(departureDate)
                .passengerCount(passengerCount)
                .cabinClass(cabinClass)
                .airlineRaw(airlineRaw)
                .minPrice(null)
                .maxPrice(maxPrice)
                .departureTimeStart(departureTimeStart)
                .departureTimeEnd(departureTimeEnd)
                .maxDurationMinutes(maxDurationMinutes)
                .directOnly(directOnly)
                .sort(sort)
                .missingFields(missingFields)
                .followUpQuestion(followUpQuestion)
                .quickActionLabels(quickActionLabels)
                .build();
    }

    /**
     * 判断文本是否为"缺槽上下文里的裸补全词"。
     *
     * <p>纯城市名、乘客数量词等在缺少"从/到/去"等结构词时，{@link #parse} 无法将其结构化为
     * 查询字段，但在 FOLLOW_UP 缺槽状态下语义上明显是在补全航班查询条件（例如用户回复"北京"、"两个人"）。
     * 本方法仅复用已维护的城市表与乘客模式做语义识别，不引入"文本长度"启发式。</p>
     */
    public boolean looksLikeSlotFiller(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        for (String city : CITY_ALIASES.keySet()) {
            if (t.contains(city)) {
                return true;
            }
        }
        return PASSENGER_PATTERN.matcher(t).find() || PASSENGER_CN_PATTERN.matcher(t).find();
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private LocalDate resolveMonthDay(int month, int day) {
        LocalDate today = LocalDate.now(clock);
        LocalDate candidate = safeDate(today.getYear(), month, day);
        if (candidate != null && candidate.isBefore(today)) {
            candidate = safeDate(today.getYear() + 1, month, day);
        }
        return candidate;
    }

    private LocalDate parseRelativeDate(String text) {
        LocalDate today = LocalDate.now(clock);
        if (text.contains("大后天")) {
            return today.plusDays(3);
        }
        if (text.contains("后天")) {
            return today.plusDays(2);
        }
        if (text.contains("明天")) {
            return today.plusDays(1);
        }
        if (text.contains("今天")) {
            return today;
        }
        if (text.contains("下周末")) {
            return saturdayOfIsoWeek(today.plusWeeks(1));
        }
        if (text.contains("这周末") || text.contains("本周末") || text.contains("周末")) {
            LocalDate saturday = saturdayOfIsoWeek(today);
            return saturday.isBefore(today) ? saturday.plusWeeks(1) : saturday;
        }

        WeekdayPhrase weekdayPhrase = parseWeekdayPhrase(text);
        if (weekdayPhrase == null) {
            return null;
        }
        if (weekdayPhrase.nextWeek()) {
            return weekdayInIsoWeek(today.plusWeeks(1), weekdayPhrase.dayOfWeek());
        }
        LocalDate candidate = weekdayInIsoWeek(today, weekdayPhrase.dayOfWeek());
        if (weekdayPhrase.explicitThisWeek()) {
            return candidate;
        }
        return candidate.isBefore(today) ? candidate.plusWeeks(1) : candidate;
    }

    private LocalDate saturdayOfIsoWeek(LocalDate date) {
        return weekdayInIsoWeek(date, DayOfWeek.SATURDAY);
    }

    private LocalDate weekdayInIsoWeek(LocalDate date, DayOfWeek dayOfWeek) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusDays(dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue());
    }

    private WeekdayPhrase parseWeekdayPhrase(String text) {
        for (Map.Entry<String, DayOfWeek> entry : weekdayMap().entrySet()) {
            String token = entry.getKey();
            if (text.contains("下个" + token) || text.contains("下" + token)) {
                return new WeekdayPhrase(entry.getValue(), true, false);
            }
            if (text.contains("这" + token) || text.contains("本" + token)) {
                return new WeekdayPhrase(entry.getValue(), false, true);
            }
            if (text.contains(token)) {
                return new WeekdayPhrase(entry.getValue(), false, false);
            }
        }
        return null;
    }

    private Map<String, DayOfWeek> weekdayMap() {
        return Map.ofEntries(
                Map.entry("周一", DayOfWeek.MONDAY), Map.entry("星期一", DayOfWeek.MONDAY),
                Map.entry("周二", DayOfWeek.TUESDAY), Map.entry("星期二", DayOfWeek.TUESDAY),
                Map.entry("周三", DayOfWeek.WEDNESDAY), Map.entry("星期三", DayOfWeek.WEDNESDAY),
                Map.entry("周四", DayOfWeek.THURSDAY), Map.entry("星期四", DayOfWeek.THURSDAY),
                Map.entry("周五", DayOfWeek.FRIDAY), Map.entry("星期五", DayOfWeek.FRIDAY),
                Map.entry("周六", DayOfWeek.SATURDAY), Map.entry("星期六", DayOfWeek.SATURDAY),
                Map.entry("周日", DayOfWeek.SUNDAY), Map.entry("星期日", DayOfWeek.SUNDAY),
                Map.entry("周天", DayOfWeek.SUNDAY), Map.entry("星期天", DayOfWeek.SUNDAY)
        );
    }

    private String[] parseKnownCityRoute(String text) {
        for (String departure : CITY_ALIASES.keySet()) {
            for (String arrival : CITY_ALIASES.keySet()) {
                if (departure.equals(arrival)) {
                    continue;
                }
                if (text.contains(departure + "到" + arrival)
                        || text.contains(departure + "飞" + arrival)
                        || text.contains(departure + "去" + arrival)) {
                    return new String[]{CITY_ALIASES.get(departure), CITY_ALIASES.get(arrival)};
                }
            }
        }
        return null;
    }

    private Integer parseChinesePassengerCount(String text) {
        return switch (text) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> null;
        };
    }

    private boolean hasAmbiguousDateExpression(String text) {
        return text.contains("最近几天")
                || text.contains("这几天")
                || text.contains("未来几天")
                || text.contains("近两天")
                || text.contains("未来一周")
                || DATE_RANGE.matcher(text).find()
                || MULTI_WEEKDAY.matcher(text).find();
    }

    private String resolveCity(String text) {
        String trimmed = text.trim();
        if (CITY_ALIASES.containsKey(trimmed)) {
            return CITY_ALIASES.get(trimmed);
        }
        for (String city : CITY_ALIASES.keySet()) {
            if (trimmed.contains(city)) {
                return city;
            }
        }
        return trimmed;
    }

    private String buildFollowUpQuestion(List<String> missingFields) {
        List<String> parts = new ArrayList<>();
        for (String field : missingFields) {
            switch (field) {
                case "departureCity" -> parts.add("出发城市");
                case "arrivalCity" -> parts.add("目的地城市");
                case "departureDate" -> parts.add("出发日期");
            }
        }
        return "请问您的" + String.join("、", parts) + "是什么？";
    }

    private List<String> buildQuickActions(List<String> missingFields,
                                            String departureCity, String arrivalCity) {
        List<String> actions = new ArrayList<>();
        if (missingFields.contains("departureCity") || missingFields.contains("arrivalCity")) {
            actions.add("我想从上海飞北京");
            actions.add("广州到成都");
        }
        if (missingFields.contains("departureDate")) {
            actions.add("明天出发");
            actions.add("后天出发");
        }
        return actions;
    }

    private record WeekdayPhrase(DayOfWeek dayOfWeek, boolean nextWeek, boolean explicitThisWeek) {
    }
}
