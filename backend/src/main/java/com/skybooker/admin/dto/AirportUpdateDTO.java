package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机场编辑表单。不含 {@code code}：code 为稳定标识，创建后不可改，
 * 由 {@link AirportDTO}（新增专用）承载。分离可避免 {@code @NotBlank(code)}
 * 把"按契约不提交 code"的编辑请求在校验层直接拦截。
 */
@Data
public class AirportUpdateDTO {

    @NotBlank(message = "机场名称不能为空")
    @Size(max = 100, message = "机场名称长度不能超过 100")
    private String name;

    @NotBlank(message = "所在城市不能为空")
    @Size(max = 50, message = "城市长度不能超过 50")
    private String city;

    @Size(max = 50, message = "省份长度不能超过 50")
    private String province;
}
