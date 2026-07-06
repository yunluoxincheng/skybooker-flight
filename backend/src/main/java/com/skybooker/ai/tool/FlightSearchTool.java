package com.skybooker.ai.tool;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.service.FlightRecommendationService;
import com.skybooker.flight.mapper.FlightMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlightSearchTool {

    private final FlightRecommendationService flightRecommendationService;
    private final FlightMapper flightMapper;

    public FlightSearchResult search(ParsedCondition condition) {
        Long resolvedAirlineId = resolveAirlineId(condition.getAirlineRaw());
        List<Map<String, Object>> flights = flightRecommendationService.recommend(condition, resolvedAirlineId);
        return new FlightSearchResult(flights, flightRecommendationService.buildSearchUrl(condition));
    }

    private Long resolveAirlineId(String airlineRaw) {
        if (airlineRaw == null || airlineRaw.isBlank()) {
            return null;
        }
        return flightMapper.findAirlineIdByCodeOrName(airlineRaw, airlineRaw);
    }
}
