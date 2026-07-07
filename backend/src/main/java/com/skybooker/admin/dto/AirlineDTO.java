package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 航司新增表单。编辑请使用 {@link AirlineUpdateDTO}（不含 code，避免 @NotBlank(code) 拦截编辑请求）。
 */
@Data
public class AirlineDTO {

    @NotBlank(message = "航司代码不能为空")
    @Size(max = 20, message = "航司代码长度不能超过 20")
    private String code;

    @NotBlank(message = "航司名称不能为空")
    @Size(max = 100, message = "航司名称长度不能超过 100")
    private String name;

    @Size(max = 255, message = "logo 地址长度不能超过 255")
    private String logoUrl;
}
