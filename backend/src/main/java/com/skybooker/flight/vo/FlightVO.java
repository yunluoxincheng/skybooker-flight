package com.skybooker.flight.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightVO {
    private Long id;
    private String flightNo;
    private Long airlineId;
    private String airlineCode;
    private String airlineName;
    private Long departureAirportId;
    private String departureAirportCode;
    private String departureAirportName;
    private String departureCity;
    private Long arrivalAirportId;
    private String arrivalAirportCode;
    private String arrivalAirportName;
    private String arrivalCity;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer durationMinutes;
    private BigDecimal basePrice;
    private Integer remainingSeats;
    private Integer totalSeats;
    private String status;
    private String publishStatus;
    private Integer directFlag;
    private String baggageAllowance;
    private BigDecimal punctualityRate;
}
