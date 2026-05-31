package com.skybooker.refund.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundVO {
    private Long id;
    private Long orderId;
    private BigDecimal refundAmount;
    private BigDecimal feeAmount;
    private String status;
    private LocalDateTime createdAt;
}
