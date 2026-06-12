package com.skybooker.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.llm")
public class AiLlmProperties {

    private boolean enabled = false;
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int timeoutMs = 8000;
    private int maxRetries = 1;

    public boolean isConfigured() {
        return enabled
                && hasText(baseUrl)
                && hasText(apiKey)
                && hasText(model);
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
