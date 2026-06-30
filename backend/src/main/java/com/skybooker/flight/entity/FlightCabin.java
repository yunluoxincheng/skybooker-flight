package com.skybooker.flight.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FlightCabin {
    private Long id;
    private Long flightId;
    private String cabinClass;
    private BigDecimal price;
    private Integer totalSeats;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
