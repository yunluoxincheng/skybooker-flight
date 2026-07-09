package com.skybooker.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelAccountDTO {

    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;
}
