package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConnectingItineraryFormDTO {
    @NotNull(message = "第一航段不能为空")
    private Long firstFlightId;
    @NotNull(message = "第二航段不能为空")
    private Long secondFlightId;
}
