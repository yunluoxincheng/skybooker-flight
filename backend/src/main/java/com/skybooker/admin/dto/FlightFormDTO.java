package com.skybooker.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FlightFormDTO {
    @NotBlank(message = "航班号不能为空")
    private String flightNo;

    @NotNull(message = "航空公司不能为空")
    private Long airlineId;

    @NotNull(message = "出发机场不能为空")
    private Long departureAirportId;

    @NotNull(message = "到达机场不能为空")
    private Long arrivalAirportId;

    @NotNull(message = "出发时间不能为空")
    private LocalDateTime departureTime;

    @NotNull(message = "到达时间不能为空")
    private LocalDateTime arrivalTime;

    @NotNull(message = "飞行时长不能为空")
    @Positive(message = "飞行时长必须为正")
    private Integer durationMinutes;

    @NotNull(message = "基础票价不能为空")
    @DecimalMin(value = "0.01", message = "基础票价必须为正")
    private BigDecimal basePrice;

    @NotNull(message = "总座位数不能为空")
    @Positive(message = "总座位数必须为正")
    private Integer totalSeats;

    private String status = "ON_TIME";
    private String publishStatus = "DRAFT";
    private Boolean directFlag = true;
    private String baggageAllowance;
    private BigDecimal punctualityRate;
}
