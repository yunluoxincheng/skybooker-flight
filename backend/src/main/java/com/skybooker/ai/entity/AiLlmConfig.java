package com.skybooker.ai.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI LLM provider 运行时配置（单行表，固定 id=1）。
 * api_key_cipher 为 AES-GCM 密文（含 IV），由 {@code LlmConfigCrypto} 加解密。
 */
@Data
public class AiLlmConfig {

    private Long id;
    private boolean enabled;
    private String baseUrl;
    private String apiKeyCipher;
    private String model;
    private int timeoutMs;
    private int maxRetries;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
