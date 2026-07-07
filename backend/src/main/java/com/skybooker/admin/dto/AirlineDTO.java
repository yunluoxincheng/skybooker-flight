package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 航司表单。新增与编辑共用：编辑时 {@code code} 字段由 service 忽略
 * （航司代码是稳定标识，创建后不可改），仅 {@code name} / {@code logoUrl} 被更新。
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
