package com.skybooker.ai.tool;

import java.util.List;
import java.util.Map;

public record FlightSearchResult(
        List<Map<String, Object>> flights,
        String searchUrl
) {
}
