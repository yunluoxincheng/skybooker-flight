package com.skybooker.order.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderSegmentPassenger {
    private Long id;
    private Long orderSegmentId;
    private Long passengerId;
    private String passengerName;
    private String passengerType;
    private Long seatId;
    private String seatNo;
    private BigDecimal ticketPrice;
}
