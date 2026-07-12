package com.skybooker.admin.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConnectingFlightCandidateQueryDTO extends PageQueryDTO {
    private String keyword;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long departureAirportId;
    private Long arrivalAirportId;
}
