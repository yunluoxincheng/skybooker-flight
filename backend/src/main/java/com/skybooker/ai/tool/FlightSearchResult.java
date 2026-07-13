package com.skybooker.ai.tool;

import java.util.List;
import java.util.Map;

public record FlightSearchResult(
        List<Map<String, Object>> flights,
        String searchUrl,
        FlightMatchLevel matchLevel,
        Map<String, Object> appliedCondition,
        List<String> relaxedFields,
        String fallbackReason
) {
}
