package com.skybooker.ai.service;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.tool.FlightSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AiReplyComposer {

    private final FlightConditionSummaryFormatter formatter;

    public String composeSearchReply(ParsedCondition requested, ParsedCondition explicit,
                                     FlightSearchResult result, String message,
                                     boolean hadActiveCondition) {
        String prefix = composeConditionUpdatedReply(explicit, message, hadActiveCondition);
        String defaultNotice = requested != null && !requested.hasDepartureDateCondition()
                ? "您还没有指定日期，我先按今天查询。" : "";
        String resultText = switch (result.matchLevel()) {
            case EXACT -> "找到了 " + result.flights().size() + " 个符合条件的航班。以下航班均为"
                    + formatter.summary(requested) + "，您可以继续比较价格和起飞时间。";
            case RELAXED -> composeRelaxedReply(requested, result);
            case PARTIAL -> composePartialReply(requested, result);
            case FALLBACK -> "暂时没有查到符合当前路线和日期的航班。下面这些是近期仍有余票的航班，仅供参考，"
                    + "与您刚才的查询条件不完全一致。您也可以修改日期或目的地后重新查询。";
        };
        return join(prefix, defaultNotice, resultText);
    }

    public String composeMissingFieldsReply(ParsedCondition condition, ParsedCondition explicit,
                                            String message, boolean hadActiveCondition) {
        String prefix = composeConditionUpdatedReply(explicit, message, hadActiveCondition);
        List<String> missing = condition == null || condition.getMissingFields() == null
                ? List.of() : condition.getMissingFields();
        String known = formatter.summary(condition);
        String question = naturalQuestion(missing);
        if ("当前条件".equals(known)) return question;
        return join(prefix, "好的，当前条件是" + known + "。" + question);
    }

    public String composeResetReply() {
        return "已清空当前航班查询条件，我们可以重新开始。";
    }

    public String composeNoResultReply(ParsedCondition condition) {
        return "暂时没有找到" + formatter.summary(condition) + "的可售航班。您可以尝试修改日期或目的地。";
    }

    private String composeRelaxedReply(ParsedCondition requested, FlightSearchResult result) {
        List<String> relaxed = result.relaxedFields().stream().map(this::fieldLabel).toList();
        String fields = quoteJoin(relaxed);
        return "暂时没有同时满足" + fields + "的航班。我保留了路线和日期等主要条件，为您找到了 "
                + result.flights().size() + " 个其他可选航班。";
    }

    private String composePartialReply(ParsedCondition requested, FlightSearchResult result) {
        String requestedDate = formatter.date(requested);
        String appliedDate = formatter.appliedDate(result.appliedCondition());
        String route = route(requested);
        return (requestedDate == null ? "原查询日期" : requestedDate) + "暂时没有" + route
                + "的可售航班。最近有航班的日期是 " + appliedDate + "，我先为您列出当天的选择。";
    }

    private String composeConditionUpdatedReply(ParsedCondition explicit, String message,
                                                boolean hadActiveCondition) {
        if (!hadActiveCondition) return "";
        if (message != null && (message.contains("时间不限") || message.contains("时段不限"))) {
            return "好的，已取消起飞时段限制，路线、日期和其他条件保持不变。";
        }
        List<String> changes = new ArrayList<>();
        addChange(changes, "出发地", formatter.fieldValue("departureCity", explicit));
        addChange(changes, "目的地", formatter.fieldValue("arrivalCity", explicit));
        addChange(changes, "出发日期", formatter.fieldValue("departureDate", explicit));
        addChange(changes, "起飞时间", formatter.fieldValue("departureTime", explicit));
        addChange(changes, "航空公司", formatter.fieldValue("airlineRaw", explicit));
        addChange(changes, "舱位", formatter.fieldValue("cabinClass", explicit));
        if (changes.isEmpty()) return "";
        return "好的，已将" + String.join("、", changes) + "，其他条件保持不变。";
    }

    private void addChange(List<String> changes, String label, String value) {
        if (value != null) changes.add(label + "改为" + value);
    }

    private String naturalQuestion(List<String> missing) {
        if (missing.equals(List.of("departureCity"))) return "请问从哪个城市出发？";
        if (missing.equals(List.of("arrivalCity"))) return "您准备飞往哪里？";
        if (missing.equals(List.of("departureDate"))) return "您准备哪天出发？";
        List<String> labels = missing.stream().map(this::fieldLabel).toList();
        return "请告诉我" + String.join("和", labels) + "。";
    }

    private String route(ParsedCondition condition) {
        if (condition != null && condition.getDepartureCity() != null && condition.getArrivalCity() != null) {
            return condition.getDepartureCity() + "飞往" + condition.getArrivalCity();
        }
        return "当前路线";
    }

    private String fieldLabel(String field) {
        return switch (field) {
            case "departureCity" -> "出发地";
            case "arrivalCity" -> "目的地";
            case "departureDate" -> "出发日期";
            case "departureTime" -> "起飞时段";
            case "airlineRaw" -> "航空公司";
            case "cabinClass" -> "舱位";
            case "price" -> "价格范围";
            case "maxDurationMinutes" -> "最长飞行时长";
            case "directOnly" -> "直飞要求";
            case "sort" -> "排序偏好";
            default -> "其他筛选条件";
        };
    }

    private String quoteJoin(List<String> values) {
        return values.stream().distinct().map(value -> "“" + value + "”").reduce((a, b) -> a + "和" + b)
                .orElse("全部筛选条件");
    }

    private String join(String... parts) {
        return java.util.Arrays.stream(parts).filter(value -> value != null && !value.isBlank())
                .reduce((a, b) -> a + b).orElse("");
    }
}
