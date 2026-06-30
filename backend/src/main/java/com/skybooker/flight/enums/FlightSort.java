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

    /** 合法舱位白名单(ECONOMY/BUSINESS/FIRST),排序拼接与配置校验共用,统一定义避免散落漂移。 */
    public static final Set<String> VALID_CABINS = Set.of("ECONOMY", "BUSINESS", "FIRST");

    /** cabinClass 是否为合法舱位(非空且在白名单内)。 */
    public static boolean isValidCabin(String cabinClass) {
        return cabinClass != null && VALID_CABINS.contains(cabinClass);
    }

    /**
     * 返回该排序维度对应的 ORDER BY SQL 片段(FlightService / FlightRecommendationService 共用)。
     * PRICE_ASC 传入合法 cabinClass 时按该舱位 flight_cabin.price 子查询排序;
     * 其余维度及无舱位回退不受 cabinClass 影响。cabinClass 拼入 ${} 前须经 isValidCabin 校验。
     */
    public String orderBy(String cabinClass) {
        boolean hasCabin = isValidCabin(cabinClass);
        return switch (this) {
            case PRICE_ASC -> hasCabin
                    ? "(SELECT MIN(fc.price) FROM flight_cabin fc WHERE fc.flight_id = f.id AND fc.cabin_class = '" + cabinClass + "') ASC, f.departure_time ASC"
                    : "f.base_price ASC, f.departure_time ASC";
            case DURATION_ASC -> "f.duration_minutes ASC, f.departure_time ASC";
            case TIME_ASC -> "f.departure_time ASC";
            case SEATS_DESC -> "f.remaining_seats DESC, f.departure_time ASC";
            case PUNCTUAL_DESC -> "f.punctuality_rate DESC, f.departure_time ASC";
            default -> "f.departure_time ASC";
        };
    }
}
