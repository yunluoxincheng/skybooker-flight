package com.skybooker.flight.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FlightSeat {
    private Long id;
    private Long flightId;
    private String seatNo;
    private String cabinClass;
    private String seatType;
    private BigDecimal price;
    private String status;
    private Integer version;
    private Long lockedByOrderId;
    private LocalDateTime lockExpireTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
