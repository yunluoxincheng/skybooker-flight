package com.skybooker.ai.parser;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder(toBuilder = true)
public class ParsedCondition {
    private String departureCity;
    private String arrivalCity;
    private LocalDate departureDate;
    private Integer passengerCount;
    private String cabinClass;
    private String airlineRaw;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private LocalTime departureTimeStart;
    private LocalTime departureTimeEnd;
    private Integer maxDurationMinutes;
    private Boolean directOnly;
    private String sort;
    private List<String> missingFields;
    private String followUpQuestion;
    private List<String> quickActionLabels;
    public boolean isComplete() {
        return departureCity != null && arrivalCity != null && departureDate != null;
    }
}
