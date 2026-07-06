package com.skybooker.ai.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParsedConditionMaps {

    private ParsedConditionMaps() {
    }

    public static Map<String, Object> toMap(ParsedCondition condition) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (condition == null) {
            return map;
        }
        if (condition.getDepartureCity() != null) map.put("departureCity", condition.getDepartureCity());
        if (condition.getArrivalCity() != null) map.put("arrivalCity", condition.getArrivalCity());
        if (condition.getDepartureDate() != null) map.put("departureDate", condition.getDepartureDate().toString());
        if (condition.getDepartureDateStart() != null) map.put("departureDateStart", condition.getDepartureDateStart().toString());
        if (condition.getDepartureDateEnd() != null) map.put("departureDateEnd", condition.getDepartureDateEnd().toString());
        if (condition.getPassengerCount() != null) map.put("passengerCount", condition.getPassengerCount());
        if (condition.getCabinClass() != null) map.put("cabinClass", condition.getCabinClass());
        if (condition.getAirlineRaw() != null) map.put("airlineRaw", condition.getAirlineRaw());
        if (condition.getMinPrice() != null) map.put("minPrice", condition.getMinPrice());
        if (condition.getMaxPrice() != null) map.put("maxPrice", condition.getMaxPrice());
        if (condition.getDepartureTimeStart() != null) map.put("departureTimeStart", condition.getDepartureTimeStart().toString());
        if (condition.getDepartureTimeEnd() != null) map.put("departureTimeEnd", condition.getDepartureTimeEnd().toString());
        if (condition.getMaxDurationMinutes() != null) map.put("maxDurationMinutes", condition.getMaxDurationMinutes());
        if (condition.getDirectOnly() != null) map.put("directOnly", condition.getDirectOnly());
        if (condition.getSort() != null) map.put("sort", condition.getSort());
        return map;
    }

    @SuppressWarnings("unchecked")
    public static ParsedCondition fromObject(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null) {
                map.put(key.toString(), item);
            }
        });
        return ParsedCondition.builder()
                .departureCity(string(map.get("departureCity")))
                .arrivalCity(string(map.get("arrivalCity")))
                .departureDate(date(map.get("departureDate")))
                .departureDateStart(date(map.get("departureDateStart")))
                .departureDateEnd(date(map.get("departureDateEnd")))
                .passengerCount(integer(map.get("passengerCount")))
                .cabinClass(string(map.get("cabinClass")))
                .airlineRaw(string(map.get("airlineRaw")))
                .minPrice(decimal(map.get("minPrice")))
                .maxPrice(decimal(map.get("maxPrice")))
                .departureTimeStart(time(map.get("departureTimeStart")))
                .departureTimeEnd(time(map.get("departureTimeEnd")))
                .maxDurationMinutes(integer(map.get("maxDurationMinutes")))
                .directOnly(bool(map.get("directOnly")))
                .sort(string(map.get("sort")))
                .missingFields(stringList(map.get("missingFields")))
                .followUpQuestion(string(map.get("followUpQuestion")))
                .quickActionLabels(stringList(map.get("quickActionLabels")))
                .build();
    }

    public static ParsedCondition recomputeRequiredFields(ParsedCondition condition) {
        if (condition == null) {
            return ParsedCondition.builder()
                    .missingFields(List.of("departureCity", "arrivalCity", "departureDate"))
                    .followUpQuestion(buildFollowUpQuestion(List.of("departureCity", "arrivalCity", "departureDate")))
                    .quickActionLabels(List.of())
                    .build();
        }

        List<String> missing = new ArrayList<>();
        if (condition.getDepartureCity() == null) missing.add("departureCity");
        if (condition.getArrivalCity() == null) missing.add("arrivalCity");
        if (!condition.hasDepartureDateCondition()) missing.add("departureDate");
        if (missing.isEmpty()) {
            return condition.toBuilder()
                    .missingFields(Collections.emptyList())
                    .followUpQuestion(null)
                    .quickActionLabels(List.of())
                    .build();
        }

        String question = condition.getFollowUpQuestion();
        if (question == null || question.isBlank()
                || (missingFieldsChanged(condition.getMissingFields(), missing)
                && !isSpecificDateFollowUp(question, missing))) {
            question = buildFollowUpQuestion(missing);
        }
        return condition.toBuilder()
                .missingFields(missing)
                .followUpQuestion(question)
                .quickActionLabels(condition.getQuickActionLabels() == null ? List.of() : condition.getQuickActionLabels())
                .build();
    }

    public static ParsedCondition normalizeForSearch(ParsedCondition condition) {
        if (condition == null || condition.getPassengerCount() != null) {
            return condition;
        }
        return condition.toBuilder().passengerCount(1).build();
    }

    public static ParsedCondition mergePending(ParsedCondition previous, ParsedCondition current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }

        ParsedCondition.ParsedConditionBuilder merged = current.toBuilder();
        if (current.getDepartureCity() == null) merged.departureCity(previous.getDepartureCity());
        if (current.getArrivalCity() == null) merged.arrivalCity(previous.getArrivalCity());
        if (current.getDepartureDate() == null) merged.departureDate(previous.getDepartureDate());
        if (current.getDepartureDateStart() == null) merged.departureDateStart(previous.getDepartureDateStart());
        if (current.getDepartureDateEnd() == null) merged.departureDateEnd(previous.getDepartureDateEnd());
        if (current.getPassengerCount() == null) merged.passengerCount(previous.getPassengerCount());
        if (current.getCabinClass() == null) merged.cabinClass(previous.getCabinClass());
        if (current.getAirlineRaw() == null) merged.airlineRaw(previous.getAirlineRaw());
        if (current.getMinPrice() == null) merged.minPrice(previous.getMinPrice());
        if (current.getMaxPrice() == null) merged.maxPrice(previous.getMaxPrice());
        if (current.getDepartureTimeStart() == null) merged.departureTimeStart(previous.getDepartureTimeStart());
        if (current.getDepartureTimeEnd() == null) merged.departureTimeEnd(previous.getDepartureTimeEnd());
        if (current.getMaxDurationMinutes() == null) merged.maxDurationMinutes(previous.getMaxDurationMinutes());
        if (current.getDirectOnly() == null) merged.directOnly(previous.getDirectOnly());
        if (current.getSort() == null) merged.sort(previous.getSort());
        return recomputeRequiredFields(merged.build());
    }

    public static String buildFollowUpQuestion(List<String> missingFields) {
        List<String> parts = new ArrayList<>();
        for (String field : missingFields) {
            switch (field) {
                case "departureCity" -> parts.add("出发城市");
                case "arrivalCity" -> parts.add("目的地城市");
                case "departureDate" -> parts.add("出发日期");
                default -> {
                }
            }
        }
        return "请问您的" + String.join("、", parts) + "是什么？";
    }

    private static boolean missingFieldsChanged(List<String> previous, List<String> current) {
        List<String> normalizedPrevious = previous == null ? List.of() : previous;
        return !normalizedPrevious.equals(current);
    }

    private static boolean isSpecificDateFollowUp(String question, List<String> missingFields) {
        return List.of("departureDate").equals(missingFields)
                && question != null
                && question.contains("一个出发日期");
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = string(item);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static LocalDate date(Object value) {
        String text = string(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime time(Object value) {
        String text = string(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
