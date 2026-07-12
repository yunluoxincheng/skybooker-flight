package com.skybooker.ai.service;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.service.ItineraryService;
import com.skybooker.itinerary.vo.ItineraryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlightRecommendationService {

    private static final int DEFAULT_LIMIT = 3;

    private final ItineraryService itineraryService;

    public List<Map<String, Object>> recommend(ParsedCondition condition, Long resolvedAirlineId) {
        FlightSearchDTO query = toSearchQuery(condition, resolvedAirlineId);
        PageResponse<ItineraryVO> journeys = itineraryService.search(query);
        return journeys.getRecords().stream()
                .map(this::toCard)
                .toList();
    }

    public String buildSearchUrl(ParsedCondition condition) {
        return buildSearchUrl(condition, null);
    }

    public String buildSearchUrl(ParsedCondition condition, Long resolvedAirlineId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/flights");
        if (condition == null) {
            return builder.build().encode().toUriString();
        }
        addQueryParam(builder, "departureCity", condition.getDepartureCity());
        addQueryParam(builder, "arrivalCity", condition.getArrivalCity());
        addQueryParam(builder, "departureDate", condition.getDepartureDate());
        addQueryParam(builder, "departureDateStart", condition.getDepartureDateStart());
        addQueryParam(builder, "departureDateEnd", condition.getDepartureDateEnd());
        addQueryParam(builder, "airlineId", resolvedAirlineId);
        addQueryParam(builder, "airlineRaw", condition.getAirlineRaw());
        addQueryParam(builder, "minPrice", condition.getMinPrice());
        addQueryParam(builder, "maxPrice", condition.getMaxPrice());
        addQueryParam(builder, "departureTimeStart", condition.getDepartureTimeStart());
        addQueryParam(builder, "departureTimeEnd", condition.getDepartureTimeEnd());
        addQueryParam(builder, "maxDurationMinutes", condition.getMaxDurationMinutes());
        addQueryParam(builder, "directOnly", condition.getDirectOnly());
        addQueryParam(builder, "sort", condition.getSort());
        addQueryParam(builder, "passengerCount", condition.getPassengerCount());
        addQueryParam(builder, "cabinClass", condition.getCabinClass());
        addQueryParam(builder, "includeSoldOut", condition.getIncludeSoldOut());
        return builder.build().encode().toUriString();
    }

    private Map<String, Object> toCard(ItineraryVO journey) {
        FlightVO first = journey.getSegments().getFirst();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", journey.getId());
        card.put("journeyType", journey.getJourneyType());
        card.put("segments", journey.getSegments());
        card.put("originCity", journey.getOriginCity());
        card.put("destinationCity", journey.getDestinationCity());
        card.put("connectionAirportCode", journey.getConnectionAirportCode());
        card.put("connectionAirportName", journey.getConnectionAirportName());
        card.put("connectionDurationMinutes", journey.getConnectionDurationMinutes());
        card.put("totalDurationMinutes", journey.getTotalDurationMinutes());
        card.put("estimatedAmount", journey.getEstimatedAmount());
        card.put("availableSeats", journey.getAvailableSeats());
        card.put("sellable", journey.getSellable());
        card.put("unavailableReason", journey.getUnavailableReason());
        card.put("detailUrl", "CONNECTING".equals(journey.getJourneyType())
                ? "/itineraries/connecting/" + journey.getId() : "/flights/" + first.getId());
        return card;
    }

    private FlightSearchDTO toSearchQuery(ParsedCondition condition, Long resolvedAirlineId) {
        FlightSearchDTO query = new FlightSearchDTO();
        query.setDepartureCity(condition.getDepartureCity());
        query.setArrivalCity(condition.getArrivalCity());
        query.setDepartureDate(condition.getDepartureDate() != null
                ? condition.getDepartureDate() : condition.getDepartureDateStart());
        query.setDepartureDateStart(condition.getDepartureDateStart());
        query.setDepartureDateEnd(condition.getDepartureDateEnd());
        query.setAirlineId(resolvedAirlineId);
        query.setMinPrice(condition.getMinPrice());
        query.setMaxPrice(condition.getMaxPrice());
        query.setDepartureTimeStart(condition.getDepartureTimeStart());
        query.setDepartureTimeEnd(condition.getDepartureTimeEnd());
        query.setMaxDurationMinutes(condition.getMaxDurationMinutes());
        query.setDirectOnly(condition.getDirectOnly());
        query.setSort(condition.getSort());
        query.setPassengerCount(condition.getPassengerCount());
        query.setCabinClass(condition.getCabinClass());
        query.setIncludeSoldOut(condition.getIncludeSoldOut());
        query.setPage(1);
        query.setSize(DEFAULT_LIMIT);
        return query;
    }

    private void addQueryParam(UriComponentsBuilder builder, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        builder.queryParam(name, value.toString());
    }

}
