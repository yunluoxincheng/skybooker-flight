package com.skybooker.ai.orchestrator;

import com.skybooker.ai.vo.AiChatReplyVO;

import java.util.Map;

public record AssistantTurnResult(
        AiChatReplyVO reply,
        Map<String, Object> extraJson,
        RecommendationLog recommendationLog
) {
}
