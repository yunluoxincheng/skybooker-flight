package com.skybooker.ai.service;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.flight.enums.FlightSort;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightVO;
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

    private final FlightMapper flightMapper;

    public List<Map<String, Object>> recommend(ParsedCondition condition, Long resolvedAirlineId) {
        String orderBy = resolveOrderBy(condition.getSort(), condition.getCabinClass());

        List<FlightVO> flights = flightMapper.searchRecommendationFlights(
                condition.getDepartureCity(),
                condition.getArrivalCity(),
                condition.getDepartureDate(),
                condition.getDepartureDateStart(),
                condition.getDepartureDateEnd(),
                resolvedAirlineId,
                condition.getMinPrice(),
                condition.getMaxPrice(),
                condition.getDepartureTimeStart(),
                condition.getDepartureTimeEnd(),
                condition.getMaxDurationMinutes(),
                condition.getDirectOnly(),
                condition.getPassengerCount(),
                condition.getCabinClass(),
                condition.getIncludeSoldOut(),
                orderBy,
                DEFAULT_LIMIT
        );

        return flights.stream()
                .map(f -> toCard(f, condition.getCabinClass()))
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

    public String buildDetailUrl(Long flightId) {
        return "/flights/" + flightId;
    }

    public String buildBookingUrl(Long flightId) {
        return "/booking/" + flightId;
    }

    private Map<String, Object> toCard(FlightVO flight, String cabinClass) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("flightId", flight.getId());
        card.put("flightNo", flight.getFlightNo());
        card.put("airlineName", flight.getAirlineName());
        card.put("departureCity", flight.getDepartureCity());
        card.put("arrivalCity", flight.getArrivalCity());
        card.put("departureTime", flight.getDepartureTime());
        card.put("arrivalTime", flight.getArrivalTime());
        card.put("durationMinutes", flight.getDurationMinutes());
        card.put("price", flight.getBasePrice());
        card.put("remainingSeats", flight.getRemainingSeats());
        card.put("status", flight.getStatus());
        if (cabinClass != null && !cabinClass.isBlank()) {
            int available = flightMapper.countAvailableSeatsByFlightAndCabin(flight.getId(), cabinClass);
            card.put("cabinAvailability", Map.of(
                    "cabinClass", cabinClass,
                    "availableSeats", available
            ));
        }
        card.put("detailUrl", buildDetailUrl(flight.getId()));
        card.put("bookingUrl", buildBookingUrl(flight.getId()));
        return card;
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

    private String resolveOrderBy(String sort, String cabinClass) {
        FlightSort flightSort = FlightSort.fromParam(sort);
        if (flightSort == null) flightSort = FlightSort.DEFAULT;
        // 排序 SQL 由 FlightSort.orderBy 统一生成(cabinClass 拼入前经 isValidCabin 校验)
        return flightSort.orderBy(cabinClass);
    }

}
