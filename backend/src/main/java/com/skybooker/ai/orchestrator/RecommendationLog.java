package com.skybooker.ai.orchestrator;

import java.util.List;
import java.util.Map;

public record RecommendationLog(
        String queryText,
        Map<String, Object> parsedCondition,
        List<Map<String, Object>> flights,
        String searchUrl
) {
}
