package com.skybooker.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员设置航班舱位配置的单项(舱位/价格/座位数)。
 * 舱位数量约束(sum=flight.totalSeats、ECONOMY≥BUSINESS≥FIRST)在 service 层校验。
 */
@Data
public class FlightCabinDTO {

    @NotNull(message = "舱位不能为空")
    @Pattern(regexp = "ECONOMY|BUSINESS|FIRST", message = "舱位必须为 ECONOMY/BUSINESS/FIRST")
    private String cabinClass;

    @NotNull(message = "舱位价格不能为空")
    @DecimalMin(value = "0.01", message = "舱位价格必须为正")
    private BigDecimal price;

    @NotNull(message = "舱位座位数不能为空")
    @Positive(message = "舱位座位数必须为正")
    private Integer totalSeats;
}
