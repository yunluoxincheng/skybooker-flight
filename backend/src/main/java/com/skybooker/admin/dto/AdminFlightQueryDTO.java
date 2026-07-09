package com.skybooker.admin.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminFlightQueryDTO extends PageQueryDTO {
    private String keyword;
    private String flightNo;
    private Long airlineId;
    private String departureCity;
    private String arrivalCity;
    private String status;
    private String publishStatus;
    private String departureDateStart;
    private String departureDateEnd;
}
