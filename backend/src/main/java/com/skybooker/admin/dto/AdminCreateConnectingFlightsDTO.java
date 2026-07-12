package com.skybooker.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminCreateConnectingFlightsDTO {
    @Valid
    @NotNull(message = "第一航段不能为空")
    private FlightFormDTO firstSegment;

    @Valid
    @NotNull(message = "第二航段不能为空")
    private FlightFormDTO secondSegment;
}
