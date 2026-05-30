package com.skybooker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendEmailCodeDTO {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "场景不能为空")
    @Pattern(regexp = "REGISTER|RESET_PASSWORD", message = "不支持的场景类型")
    private String scene;
}
