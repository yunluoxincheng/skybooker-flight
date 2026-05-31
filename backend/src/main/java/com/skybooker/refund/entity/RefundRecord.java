package com.skybooker.refund.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundRecord {
    private Long id;
    private Long orderId;
    private Long userId;
    private String reason;
    private BigDecimal refundAmount;
    private BigDecimal feeAmount;
    private String status;
    private LocalDateTime createdAt;
}
