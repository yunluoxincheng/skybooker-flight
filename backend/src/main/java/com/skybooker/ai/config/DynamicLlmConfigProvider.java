package com.skybooker.ai.config;

import com.skybooker.ai.entity.AiLlmConfig;
import com.skybooker.ai.mapper.AiLlmConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 提供 LLM 当前生效配置的运行时读取入口，支持后台热更新。
 *
 * <p>来源优先级：DB {@code ai_llm_config}（id=1）有行且可解密 → 使用 DB 值（source={@link #SOURCE_DB}）；
 * 否则 fallback 到 {@link AiLlmProperties}（环境变量 / application.yml，source={@link #SOURCE_ENV}）。
 *
 * <p>内置 5 秒 TTL 内存缓存（{@code volatile} 快照），{@link #invalidateCache()} 在管理员 PUT 后调用以立即生效。
 * AI 接口已有限流，QPS 低，无需复杂并发控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicLlmConfigProvider {

    private static final Duration TTL = Duration.ofSeconds(5);
    public static final String SOURCE_DB = "db";
    public static final String SOURCE_ENV = "env-default";

    private final AiLlmConfigMapper configMapper;
    private final AiLlmProperties properties;
    private final LlmConfigCrypto crypto;

    private volatile Cached snapshot;

    /**
     * 返回当前生效配置。单次 AI 请求应在入口调用一次，沿调用链传参（见 design 的 per-request 快照机制）。
     */
    public LlmEffectiveConfig getConfig() {
        Cached current = snapshot;
        if (current != null && !current.isExpired()) {
            return current.config;
        }
        LlmEffectiveConfig fresh = loadFresh();
        snapshot = new Cached(fresh, System.nanoTime());
        return fresh;
    }

    /**
     * PUT 写入后调用，使下一次请求立即生效新值（无需等待 TTL 过期）。
     */
    public void invalidateCache() {
        snapshot = null;
    }

    private LlmEffectiveConfig loadFresh() {
        AiLlmConfig row;
        try {
            row = configMapper.findActive();
        } catch (Exception e) {
            log.warn("读取 ai_llm_config 失败，fallback 环境变量默认值: {}", e.getMessage());
            return fromProperties();
        }
        if (row == null) {
            return fromProperties();
        }
        if (!crypto.isAvailable()) {
            log.warn("DB 存在加密 LLM 配置但 AI_CONFIG_ENC_KEY 不可用，fallback 环境变量默认值");
            return fromProperties();
        }
        String apiKey;
        try {
            apiKey = crypto.decrypt(row.getApiKeyCipher());
        } catch (Exception e) {
            log.warn("DB 中 LLM apiKey 解密失败，fallback 环境变量默认值: {}", e.getMessage());
            return fromProperties();
        }
        return new LlmEffectiveConfig(
                row.isEnabled(),
                row.getBaseUrl(),
                apiKey,
                row.getModel(),
                row.getTimeoutMs(),
                row.getMaxRetries(),
                SOURCE_DB);
    }

    private LlmEffectiveConfig fromProperties() {
        return new LlmEffectiveConfig(
                properties.isEnabled(),
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getModel(),
                properties.getTimeoutMs(),
                properties.getMaxRetries(),
                SOURCE_ENV);
    }

    private record Cached(LlmEffectiveConfig config, long loadedAtNanos) {
        boolean isExpired() {
            return System.nanoTime() - loadedAtNanos >= TTL.toNanos();
        }
    }
}
