package com.skybooker.flight.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 航班舱位视图:含实时余座(从 flight_seat AVAILABLE 计算)。
 */
@Data
@NoArgsConstructor
public class FlightCabinVO {
    private String cabinClass;
    private BigDecimal price;
    private Integer totalSeats;
    private Integer availableSeats;
}
