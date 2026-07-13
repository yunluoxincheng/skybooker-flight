package com.skybooker.ai.service;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.parser.ParsedConditionMaps;
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
        String update = composeConditionUpdate(explicit, requested, message, hadActiveCondition);
        String defaultNotice = requested != null && !requested.hasDepartureDateCondition()
                ? "您未指定日期，已按今天查询" : "";
        String context = joinClauses(update, defaultNotice);
        String resultText = switch (result.matchLevel()) {
            case EXACT -> withContext(context, "找到 " + result.flights().size() + " 个"
                    + (update.contains("当前条件") ? "" : formatter.summary(
                            ParsedConditionMaps.fromObject(result.appliedCondition())) + "的") + "航班。");
            case RELAXED -> composeRelaxedReply(requested, result);
            case PARTIAL -> composePartialReply(requested, result);
            case FALLBACK -> (requested != null && !requested.hasDepartureDateCondition()
                    ? "按默认的今天查询后，仍未找到符合当前条件的航班"
                    : "未找到符合当前条件的航班") + "，下面推荐 " + result.flights().size()
                    + " 个近期其他航班，路线和日期可能不同。";
        };
        return resultText;
    }

    public String composeMissingFieldsReply(ParsedCondition condition, ParsedCondition explicit,
                                            String message, boolean hadActiveCondition) {
        String prefix = composeConditionUpdate(explicit, condition, message, hadActiveCondition);
        List<String> missing = condition == null || condition.getMissingFields() == null
                ? List.of() : condition.getMissingFields();
        String known = formatter.summary(condition);
        String question = naturalQuestion(missing);
        if ("当前条件".equals(known)) return question;
        return (prefix.isBlank() ? "当前条件是" : prefix + "；当前条件是")
                + known + "。" + question;
    }

    public String composeResetReply() {
        return "已清空当前航班查询条件，我们可以重新开始。";
    }

    public String composeNoResultReply(ParsedCondition condition) {
        return "暂时没有找到" + formatter.summary(condition) + "的可售航班。您可以尝试修改日期或目的地。";
    }

    private String composeRelaxedReply(ParsedCondition requested, FlightSearchResult result) {
        String fields = relaxedDescription(result.relaxedFields(), null);
        ParsedCondition applied = ParsedConditionMaps.fromObject(result.appliedCondition());
        String retained = retainedSummary(requested, applied);
        String retainedText = "当前条件".equals(retained) ? "" : retained + "的";
        String defaultPrefix = requested != null && !requested.hasDepartureDateCondition()
                ? "按默认的今天查询后，" : "";
        return defaultPrefix + "当前筛选暂时没有完全匹配，下面是 " + result.flights().size() + " 个"
                + retainedText + "可选航班；已放宽" + fields + "。";
    }

    private String composePartialReply(ParsedCondition requested, FlightSearchResult result) {
        String requestedDate = formatter.date(requested);
        String appliedDate = formatter.appliedDate(result.appliedCondition());
        String route = route(requested);
        String otherRelaxedFields = relaxedDescription(result.relaxedFields(), "departureDate");
        String otherRelaxedNotice = otherRelaxedFields.isEmpty()
                ? ""
                : "同时放宽了" + otherRelaxedFields + "。";
        return (requestedDate == null ? "按默认的今天查询时，" : requestedDate) + route
                + "暂无可售航班，最近有航班的日期是 " + appliedDate + "，下面列出当天 "
                + result.flights().size() + " 个选择。"
                + otherRelaxedNotice;
    }

    private String composeConditionUpdate(ParsedCondition explicit, ParsedCondition active, String message,
                                          boolean hadActiveCondition) {
        if (!hadActiveCondition) return "";
        List<String> changes = new ArrayList<>();
        if (message != null && (message.contains("时间不限") || message.contains("时段不限"))) {
            changes.add("已取消起飞时段限制");
        }
        addChange(changes, "出发地", formatter.fieldValue("departureCity", explicit));
        addChange(changes, "目的地", formatter.fieldValue("arrivalCity", explicit));
        addChange(changes, "出发日期", formatter.fieldValue("departureDate", explicit));
        addChange(changes, "起飞时间", formatter.fieldValue("departureTime", explicit));
        addChange(changes, "航空公司", formatter.fieldValue("airlineRaw", explicit));
        addChange(changes, "舱位", formatter.fieldValue("cabinClass", explicit));
        if (changes.isEmpty()) return "";
        if (changes.size() == 1) return changes.getFirst();
        String summary = formatter.summary(active);
        if (changes.getFirst().equals("已取消起飞时段限制")) {
            return "已取消起飞时段限制，当前条件更新为" + summary;
        }
        return "当前条件已更新为" + summary;
    }

    private void addChange(List<String> changes, String label, String value) {
        if (value != null) changes.add("已将" + label + "改为" + value);
    }

    private String retainedSummary(ParsedCondition requested, ParsedCondition applied) {
        if (requested == null || applied == null) return "当前条件";
        ParsedCondition.ParsedConditionBuilder retained = ParsedCondition.builder();
        if (java.util.Objects.equals(requested.getDepartureCity(), applied.getDepartureCity())) {
            retained.departureCity(requested.getDepartureCity());
        }
        if (java.util.Objects.equals(requested.getArrivalCity(), applied.getArrivalCity())) {
            retained.arrivalCity(requested.getArrivalCity());
        }
        if (requested.getDepartureDate() != null
                && java.util.Objects.equals(requested.getDepartureDate(), applied.getDepartureDate())) {
            retained.departureDate(requested.getDepartureDate());
        }
        if (requested.getDepartureDateStart() != null && requested.getDepartureDateEnd() != null
                && java.util.Objects.equals(requested.getDepartureDateStart(), applied.getDepartureDateStart())
                && java.util.Objects.equals(requested.getDepartureDateEnd(), applied.getDepartureDateEnd())) {
            retained.departureDateStart(requested.getDepartureDateStart())
                    .departureDateEnd(requested.getDepartureDateEnd());
        }
        if (java.util.Objects.equals(requested.getPassengerCount(), applied.getPassengerCount())) {
            retained.passengerCount(requested.getPassengerCount());
        }
        return formatter.summary(retained.build());
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

    private String relaxedDescription(List<String> fields, String excludedField) {
        List<String> labels = fields.stream()
                .filter(field -> !field.equals(excludedField))
                .filter(field -> !"sort".equals(field))
                .map(this::fieldLabel)
                .distinct()
                .toList();
        if (labels.isEmpty()) return "";
        if (labels.size() > 2) return "部分筛选条件";
        return String.join("和", labels) + "条件";
    }

    private String join(String... parts) {
        return java.util.Arrays.stream(parts).filter(value -> value != null && !value.isBlank())
                .reduce((a, b) -> a + b).orElse("");
    }

    private String joinClauses(String... parts) {
        return java.util.Arrays.stream(parts).filter(value -> value != null && !value.isBlank())
                .reduce((a, b) -> a + "；" + b).orElse("");
    }

    private String withContext(String context, String result) {
        return context == null || context.isBlank() ? result : context + "；" + result;
    }
}
