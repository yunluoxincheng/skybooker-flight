package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistPerformanceVO {
    private Long submittedCount;
    private Long pendingPaymentCount;
    private Long waitingCount;
    private Long successCount;
    private Long failedCount;
    private Long cancelledCount;
    private Long refundedCount;
    private Long expiredCount;
    private BigDecimal payAmount;
    private BigDecimal refundAmount;
}
