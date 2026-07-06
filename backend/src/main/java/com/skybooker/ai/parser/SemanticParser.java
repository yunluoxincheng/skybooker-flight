package com.skybooker.ai.parser;

import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.service.DomainIntentRouter;
import com.skybooker.ai.state.ConversationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticParser {

    private final DomainIntentRouter domainIntentRouter;
    private final LlmIntentParserService llmIntentParserService;
    private final IntentParserService ruleIntentParserService;

    public SemanticParseResult parse(String message, ConversationState state) {
        DomainIntentRouter.RouteResult route = domainIntentRouter.routeWithState(message, state);
        ParsedCondition condition = null;
        boolean llmParsed = false;

        if (route.intent() == DomainIntent.FLIGHT_QUERY
                || route.intent() == DomainIntent.FLIGHT_QUERY_CONTINUATION) {
            if (route.llmConfig().isConfigured()) {
                try {
                    condition = llmIntentParserService.parse(message, route.llmConfig());
                    llmParsed = true;
                } catch (LlmIntentParseException e) {
                    log.warn("LLM semantic slot parsing failed, falling back to rule parser: {}",
                            sanitizeErrorMessage(e.getMessage()));
                }
            }
            if (condition == null) {
                condition = ruleIntentParserService.parse(message);
            }
        }

        return new SemanticParseResult(route.intent(), condition, route.llmConfig(),
                route.llmClassified(), llmParsed);
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replaceAll("(?i)(Authorization\\s*:\\s*)(Bearer\\s+)?[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll("(?i)((?:x-)?api-key\\s*[:=]?\\s*)[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9._-]+", "sk-***");
    }
}
