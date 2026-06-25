package com.skybooker.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新 AI LLM 配置的请求体。
 *
 * <p>{@code apiKey} 可选：传 {@code null}/省略 = 保留现有密钥；传非空 = 覆写；传纯空白触发校验失败。
 */
@Data
public class AiLlmConfigDTO {

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    private String baseUrl;

    private String apiKey;

    private String model;

    private Integer timeoutMs;

    private Integer maxRetries;
}
