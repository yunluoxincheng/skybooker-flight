package com.skybooker.flight.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Flight {
    private Long id;
    private String flightNo;
    private Long airlineId;
    private Long departureAirportId;
    private Long arrivalAirportId;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer durationMinutes;
    private BigDecimal basePrice;
    private Integer remainingSeats;
    private Integer totalSeats;
    private String status;
    private String publishStatus;
    private Boolean directFlag;
    private String baggageAllowance;
    private BigDecimal punctualityRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
