package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机场表单。新增与编辑共用：编辑时 {@code code} 字段由 service 忽略
 * （机场代码是稳定标识，创建后不可改），仅 {@code name} / {@code city} / {@code province} 被更新。
 */
@Data
public class AirportDTO {

    @NotBlank(message = "机场代码不能为空")
    @Size(max = 20, message = "机场代码长度不能超过 20")
    private String code;

    @NotBlank(message = "机场名称不能为空")
    @Size(max = 100, message = "机场名称长度不能超过 100")
    private String name;

    @NotBlank(message = "所在城市不能为空")
    @Size(max = 50, message = "城市长度不能超过 50")
    private String city;

    @Size(max = 50, message = "省份长度不能超过 50")
    private String province;
}
