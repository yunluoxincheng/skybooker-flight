package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCancelOrderDTO {

    @NotBlank(message = "取消原因不能为空")
    @Size(max = 100, message = "取消原因不超过 100 字")
    private String reason;
}
