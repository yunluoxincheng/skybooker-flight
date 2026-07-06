package com.skybooker.ai.tool;

import java.util.List;
import java.util.Map;

public record DestinationRecommendation(
        String replyText,
        String primaryCity,
        List<String> candidateCities,
        List<Map<String, String>> quickActions
) {
}
