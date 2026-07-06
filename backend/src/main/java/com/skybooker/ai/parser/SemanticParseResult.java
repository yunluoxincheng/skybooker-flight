package com.skybooker.ai.parser;

import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.enums.DomainIntent;

public record SemanticParseResult(
        DomainIntent intent,
        ParsedCondition condition,
        LlmEffectiveConfig llmConfig,
        boolean llmClassified,
        boolean llmParsed
) {
}
