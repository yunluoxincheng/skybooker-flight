package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI LLM 配置的脱敏响应。{@code apiKey} 永远是脱敏值（如 {@code sk****wxyz}）或空串，
 * 不会返回明文。{@code source} 标识配置来源：{@code db}（后台管理记录）/ {@code env-default}（环境变量 fallback）。
 */
@Data
@AllArgsConstructor
public class AiLlmConfigVO {

    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model;
    private int timeoutMs;
    private int maxRetries;
    private String source;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
