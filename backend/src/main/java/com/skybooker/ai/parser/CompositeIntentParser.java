package com.skybooker.ai.parser;

import com.skybooker.ai.config.AiLlmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class CompositeIntentParser implements IntentParser {

    private final AiLlmProperties properties;
    private final LlmIntentParserService llmIntentParserService;
    private final IntentParserService ruleIntentParserService;

    @Override
    public ParsedCondition parse(String message) {
        if (!properties.isConfigured()) {
            return ruleIntentParserService.parse(message);
        }

        try {
            return llmIntentParserService.parse(message);
        } catch (LlmIntentParseException e) {
            log.warn("LLM intent parsing failed, falling back to rule parser: {}", e.getMessage());
            return ruleIntentParserService.parse(message);
        }
    }
}
