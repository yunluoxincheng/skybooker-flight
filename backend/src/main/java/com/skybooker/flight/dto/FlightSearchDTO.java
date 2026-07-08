package com.skybooker.flight.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class FlightSearchDTO {
    private String flightNo;
    private String departureCity;
    private String arrivalCity;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate departureDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate departureDateStart;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate departureDateEnd;

    private Long airlineId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime departureTimeStart;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime departureTimeEnd;

    private Integer maxDurationMinutes;
    private Boolean directOnly;
    private String status;
    private String sort;
    private Integer passengerCount;
    private String cabinClass;
    private Boolean includeSoldOut;

    private Integer page = 1;
    private Integer size = 10;
}
