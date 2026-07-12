package com.skybooker.itinerary.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FareCalendarVO {
    private LocalDate date;
    private BigDecimal lowestPrice;
}
