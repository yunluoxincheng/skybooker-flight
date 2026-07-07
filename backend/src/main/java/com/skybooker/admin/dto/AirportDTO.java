package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机场新增表单。编辑请使用 {@link AirportUpdateDTO}（不含 code，避免 @NotBlank(code) 拦截编辑请求）。
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
