package com.skybooker.waitlist.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WaitlistOrder {
    private Long id;
    private String waitlistNo;
    private Long userId;
    private Long flightId;
    private Long ticketOrderId;
    private Integer passengerCount;
    private String cabinClass;
    private BigDecimal payAmount;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime paidAt;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;
    private String lastSkipReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
