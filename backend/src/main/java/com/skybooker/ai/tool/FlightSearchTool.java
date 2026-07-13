package com.skybooker.ai.tool;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.service.FlightRecommendationService;
import com.skybooker.flight.mapper.FlightMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlightSearchTool {

    private final FlightRecommendationService flightRecommendationService;
    private final FlightMapper flightMapper;
    private final Clock businessClock;

    private static final int FUTURE_SEARCH_DAYS = 30;

    public FlightSearchResult search(ParsedCondition condition) {
        Long resolvedAirlineId = resolveAirlineId(condition.getAirlineRaw());
        boolean unresolvedAirline = condition.getAirlineRaw() != null && resolvedAirlineId == null;
        boolean defaultDate = !condition.hasDepartureDateCondition();
        ParsedCondition dated = defaultDate
                ? condition.toBuilder().departureDate(LocalDate.now(businessClock)).build()
                : condition;
        List<Map<String, Object>> flights = unresolvedAirline
                ? List.of() : recommend(dated, resolvedAirlineId);
        if (!unresolvedAirline && !flights.isEmpty()) {
            return result(flights, dated, resolvedAirlineId, FlightMatchLevel.EXACT, List.of(), "");
        }

        ParsedCondition relaxed = removeSecondaryFilters(dated);
        List<String> relaxedFields = secondaryFields(dated);
        if (!relaxedFields.isEmpty()) {
            flights = recommend(relaxed, null);
            if (!flights.isEmpty()) {
                return result(flights, relaxed, null, FlightMatchLevel.RELAXED, relaxedFields,
                        "未找到符合全部筛选条件的航班");
            }
        }

        ParsedCondition core = relaxed;
        LocalDate futureSearchBase = core.getDepartureDate() != null
                ? core.getDepartureDate() : LocalDate.now(businessClock);
        if (futureSearchBase.isBefore(LocalDate.now(businessClock))) {
            futureSearchBase = LocalDate.now(businessClock);
        }
        for (int offset = 1; offset <= FUTURE_SEARCH_DAYS; offset++) {
            ParsedCondition candidate = core.toBuilder()
                    .departureDate(futureSearchBase.plusDays(offset))
                    .departureDateStart(null).departureDateEnd(null).build();
            flights = recommend(candidate, null);
            if (!flights.isEmpty()) {
                List<String> changed = new ArrayList<>(relaxedFields);
                changed.add("departureDate");
                return result(flights, candidate, null, FlightMatchLevel.PARTIAL, changed,
                        defaultDate ? "当前日期没有匹配航班" : "用户指定日期没有匹配航班");
            }
        }

        for (int offset = 0; offset <= FUTURE_SEARCH_DAYS; offset++) {
            ParsedCondition fallback = ParsedCondition.builder()
                    .departureDate(LocalDate.now(businessClock).plusDays(offset))
                    .passengerCount(condition.getPassengerCount()).build();
            flights = recommend(fallback, null);
            if (!flights.isEmpty()) {
                return result(flights, fallback, null, FlightMatchLevel.FALLBACK,
                        List.of("departureCity", "arrivalCity", "departureDate", "filters"),
                        "没有找到符合条件的航班");
            }
        }
        return result(List.of(), dated, resolvedAirlineId, FlightMatchLevel.FALLBACK, List.of(),
                "未来暂无可售航班");
    }

    private List<Map<String, Object>> recommend(ParsedCondition condition, Long airlineId) {
        return flightRecommendationService.recommend(condition, airlineId);
    }

    private FlightSearchResult result(List<Map<String, Object>> flights, ParsedCondition applied,
                                      Long airlineId, FlightMatchLevel level, List<String> fields, String reason) {
        return new FlightSearchResult(flights, flightRecommendationService.buildSearchUrl(applied, airlineId),
                level, com.skybooker.ai.parser.ParsedConditionMaps.toMap(applied), fields, reason);
    }

    private ParsedCondition removeSecondaryFilters(ParsedCondition value) {
        return value.toBuilder().airlineRaw(null).cabinClass(null).minPrice(null).maxPrice(null)
                .departureTimeStart(null).departureTimeEnd(null).maxDurationMinutes(null)
                .directOnly(null).sort(null).build();
    }

    private List<String> secondaryFields(ParsedCondition value) {
        List<String> fields = new ArrayList<>();
        if (value.getAirlineRaw() != null) fields.add("airlineRaw");
        if (value.getCabinClass() != null) fields.add("cabinClass");
        if (value.getMinPrice() != null || value.getMaxPrice() != null) fields.add("price");
        if (value.getDepartureTimeStart() != null || value.getDepartureTimeEnd() != null) fields.add("departureTime");
        if (value.getMaxDurationMinutes() != null) fields.add("maxDurationMinutes");
        if (value.getDirectOnly() != null) fields.add("directOnly");
        if (value.getSort() != null) fields.add("sort");
        return fields;
    }

    private Long resolveAirlineId(String airlineRaw) {
        if (airlineRaw == null || airlineRaw.isBlank()) {
            return null;
        }
        return flightMapper.findAirlineIdByCodeOrName(airlineRaw, airlineRaw);
    }
}
