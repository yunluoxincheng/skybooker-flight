package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 航司编辑表单。不含 {@code code}：code 为稳定标识，创建后不可改，
 * 由 {@link AirlineDTO}（新增专用）承载。分离可避免 {@code @NotBlank(code)}
 * 把"按契约不提交 code"的编辑请求在校验层直接拦截。
 */
@Data
public class AirlineUpdateDTO {

    @NotBlank(message = "航司名称不能为空")
    @Size(max = 100, message = "航司名称长度不能超过 100")
    private String name;

    @Size(max = 255, message = "logo 地址长度不能超过 255")
    private String logoUrl;
}
