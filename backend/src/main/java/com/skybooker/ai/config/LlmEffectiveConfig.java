package com.skybooker.ai.config;

/**
 * 一次 AI 请求内固定的 LLM 配置快照（不可变）。
 * 由 {@link DynamicLlmConfigProvider} 在请求入口读取一次，沿调用链显式传参，
 * 避免单次请求跨越 TTL 边界时读到不一致配置。
 *
 * @param source     "db" = 后台管理的 ai_llm_config 记录；"env-default" = 环境变量 fallback
 */
public record LlmEffectiveConfig(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutMs,
        int maxRetries,
        String source
) {

    /**
     * 决定走 LLM 还是规则解析：与 {@link AiLlmProperties#isConfigured()} 保持一致语义。
     */
    public boolean isConfigured() {
        return enabled && hasText(baseUrl) && hasText(apiKey) && hasText(model);
    }

    public int normalizedTimeoutMs() {
        return timeoutMs > 0 ? timeoutMs : 8000;
    }

    public int normalizedMaxRetries() {
        return Math.max(maxRetries, 0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
