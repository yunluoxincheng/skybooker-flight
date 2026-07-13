package com.skybooker.ai.service;

import com.skybooker.ai.parser.ParsedCondition;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FlightConditionSummaryFormatter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M 月 d 日");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final Clock businessClock;

    public FlightConditionSummaryFormatter(Clock businessClock) {
        this.businessClock = businessClock;
    }

    public String summary(ParsedCondition condition) {
        if (condition == null) return "当前条件";
        List<String> parts = new ArrayList<>();
        if (condition.getDepartureCity() != null && condition.getArrivalCity() != null) {
            parts.add(condition.getDepartureCity() + "飞往" + condition.getArrivalCity());
        } else if (condition.getDepartureCity() != null) {
            parts.add("从" + condition.getDepartureCity() + "出发");
        } else if (condition.getArrivalCity() != null) {
            parts.add("飞往" + condition.getArrivalCity());
        }
        String date = date(condition);
        String time = time(condition.getDepartureTimeStart(), condition.getDepartureTimeEnd());
        if (date != null && time != null) {
            parts.add(date + time + "起飞");
        } else if (date != null) {
            parts.add(date + "出发");
        } else if (time != null) {
            parts.add(time + "起飞");
        }
        if (condition.getCabinClass() != null) parts.add(cabin(condition.getCabinClass()));
        if (condition.getAirlineRaw() != null) parts.add(condition.getAirlineRaw());
        if (condition.getPassengerCount() != null && condition.getPassengerCount() > 1) {
            parts.add(condition.getPassengerCount() + " 位乘客");
        }
        return parts.isEmpty() ? "当前条件" : String.join("、", parts);
    }

    public String date(ParsedCondition condition) {
        if (condition == null) return null;
        if (condition.getDepartureDate() != null) return date(condition.getDepartureDate());
        if (condition.getDepartureDateStart() != null && condition.getDepartureDateEnd() != null) {
            return date(condition.getDepartureDateStart()) + "至" + date(condition.getDepartureDateEnd());
        }
        return null;
    }

    public String fieldValue(String field, ParsedCondition condition) {
        if (condition == null) return null;
        return switch (field) {
            case "departureCity" -> condition.getDepartureCity();
            case "arrivalCity" -> condition.getArrivalCity();
            case "departureDate" -> date(condition);
            case "departureTime" -> time(condition.getDepartureTimeStart(), condition.getDepartureTimeEnd());
            case "airlineRaw" -> condition.getAirlineRaw();
            case "cabinClass" -> cabin(condition.getCabinClass());
            default -> null;
        };
    }

    public String appliedDate(Map<String, Object> appliedCondition) {
        if (appliedCondition == null) return null;
        Object value = appliedCondition.get("departureDate");
        if (value == null) return null;
        try {
            return date(LocalDate.parse(value.toString()));
        } catch (Exception ignored) {
            return value.toString();
        }
    }

    private String date(LocalDate value) {
        LocalDate today = LocalDate.now(businessClock);
        if (value.equals(today)) return "今天";
        if (value.equals(today.plusDays(1))) return "明天";
        return value.format(DATE_FORMAT);
    }

    private String time(LocalTime start, LocalTime end) {
        if (start == null && end == null) return null;
        if (LocalTime.of(0, 0).equals(start) && LocalTime.of(6, 0).equals(end)) return "凌晨";
        if (LocalTime.of(6, 0).equals(start) && LocalTime.NOON.equals(end)) return "上午";
        if (LocalTime.NOON.equals(start) && LocalTime.of(18, 0).equals(end)) return "下午";
        if (LocalTime.of(18, 0).equals(start)) return "晚间";
        return (start == null ? "" : start.format(TIME_FORMAT)) + "–"
                + (end == null ? "" : end.format(TIME_FORMAT));
    }

    private String cabin(String value) {
        if (value == null) return null;
        return switch (value.toUpperCase()) {
            case "ECONOMY" -> "经济舱";
            case "BUSINESS" -> "商务舱";
            case "FIRST" -> "头等舱";
            default -> value;
        };
    }
}
