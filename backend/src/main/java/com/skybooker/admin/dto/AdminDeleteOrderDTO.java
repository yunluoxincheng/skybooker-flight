package com.skybooker.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminDeleteOrderDTO {

    @Size(max = 100, message = "作废原因不超过 100 字")
    private String reason;
}
