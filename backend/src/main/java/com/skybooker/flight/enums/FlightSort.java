package com.skybooker.flight.enums;

import java.util.Map;
import java.util.Set;

public enum FlightSort {
    DEFAULT,
    PRICE_ASC,
    DURATION_ASC,
    TIME_ASC,
    SEATS_DESC,
    PUNCTUAL_DESC;

    private static final Set<String> VALID_NAMES = Set.of(
            "DEFAULT", "COMPREHENSIVE",
            "PRICE_ASC", "DURATION_ASC", "TIME_ASC", "SEATS_DESC", "PUNCTUAL_DESC"
    );

    private static final Map<String, FlightSort> ALIAS_MAP = Map.of(
            "DEFAULT", DEFAULT,
            "COMPREHENSIVE", DEFAULT,
            "PRICE_ASC", PRICE_ASC,
            "DURATION_ASC", DURATION_ASC,
            "TIME_ASC", TIME_ASC,
            "SEATS_DESC", SEATS_DESC,
            "PUNCTUAL_DESC", PUNCTUAL_DESC
    );

    public static FlightSort fromParam(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT;
        }
        String upper = sort.trim().toUpperCase();
        if (VALID_NAMES.contains(upper)) {
            return ALIAS_MAP.get(upper);
        }
        return null;
    }
}
