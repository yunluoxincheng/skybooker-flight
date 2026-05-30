package com.skybooker.flight.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightSeatVO {
    private Long id;
    private String seatNo;
    private String cabinClass;
    private String seatType;
    private BigDecimal price;
    private String status;
    private Integer version;
}
