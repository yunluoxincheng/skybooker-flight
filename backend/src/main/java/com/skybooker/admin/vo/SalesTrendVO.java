package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesTrendVO {
    private String period;
    private Long activeOrderCount;
    private Long passengerCount;
    private BigDecimal revenue;
}
