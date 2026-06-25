package com.skybooker.ai.parser;

import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.ai.config.LlmEffectiveConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class CompositeIntentParser implements IntentParser {

    private final DynamicLlmConfigProvider configProvider;
    private final LlmIntentParserService llmIntentParserService;
    private final IntentParserService ruleIntentParserService;

    @Override
    public ParsedCondition parse(String message) {
        // 入口唯一读取点：一次 AI 请求内配置固定（见 design 的 per-request 快照机制），
        // 沿调用链显式传参，避免 TTL 边界处读到不一致配置。
        LlmEffectiveConfig cfg = configProvider.getConfig();
        if (!cfg.isConfigured()) {
            return ruleIntentParserService.parse(message);
        }

        try {
            return llmIntentParserService.parse(message, cfg);
        } catch (LlmIntentParseException e) {
            log.warn("LLM intent parsing failed, falling back to rule parser: {}", e.getMessage());
            return ruleIntentParserService.parse(message);
        }
    }
}
