package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightLoadFactorVO {
    private Long flightId;
    private String flightNo;
    private String airlineName;
    private String routeLabel;
    private LocalDateTime departureTime;
    private Integer totalSeats;
    private Long soldPassengerCount;
    private BigDecimal loadFactorPercent;
}
