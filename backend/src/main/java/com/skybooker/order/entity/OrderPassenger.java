package com.skybooker.order.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderPassenger {
    private Long id;
    private Long orderId;
    private Long passengerId;
    private String passengerName;
    private String passengerType;
    private Long seatId;
    private String seatNo;
    private BigDecimal ticketPrice;
    private LocalDateTime createdAt;
}
