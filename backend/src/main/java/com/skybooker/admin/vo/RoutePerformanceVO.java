package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutePerformanceVO {
    private String departureCity;
    private String arrivalCity;
    private String routeLabel;
    private Long activeOrderCount;
    private Long passengerCount;
    private BigDecimal revenue;
    private BigDecimal refundAmount;
    private BigDecimal netRevenue;
}
