package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundTrendVO {
    private String period;
    private Long refundCount;
    private BigDecimal refundAmount;
}
