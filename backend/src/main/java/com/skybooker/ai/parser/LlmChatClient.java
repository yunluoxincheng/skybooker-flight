package com.skybooker.ai.parser;

import com.skybooker.ai.config.LlmEffectiveConfig;

public interface LlmChatClient {

    /**
     * 调用 LLM provider 完成一次补全。{@code cfg} 为本次请求入口读取的配置快照，
     * 沿调用链显式传入，保证单次请求内配置一致（见 design 的 per-request 快照机制）。
     */
    String complete(String systemPrompt, String userPrompt, LlmEffectiveConfig cfg);
}
