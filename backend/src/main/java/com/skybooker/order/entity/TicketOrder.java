package com.skybooker.order.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketOrder {
    public static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_CHANGED = "CHANGED";
    public static final String STATUS_CHANGE_PENDING = "CHANGE_PENDING";
    public static final String STATUS_VOIDED = "VOIDED";

    private Long id;
    private String orderNo;
    private Long userId;
    private Long flightId;
    private String status;
    private BigDecimal ticketAmount;
    private BigDecimal airportFee;
    private BigDecimal fuelFee;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
