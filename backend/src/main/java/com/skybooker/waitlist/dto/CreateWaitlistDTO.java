package com.skybooker.waitlist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateWaitlistDTO {

    @NotNull(message = "航班ID不能为空")
    private Long flightId;

    @NotNull(message = "舱等不能为空")
    private String cabinClass;

    @NotEmpty(message = "乘机人ID列表不能为空")
    private List<Long> passengerIds;
}
