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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlightRecommendationService {

    private static final int DEFAULT_LIMIT = 3;
    private static final Set<String> VALID_CABINS = Set.of("ECONOMY", "BUSINESS", "FIRST");

    private final FlightMapper flightMapper;

    public List<Map<String, Object>> recommend(ParsedCondition condition, Long resolvedAirlineId) {
        String orderBy = resolveOrderBy(condition.getSort(), condition.getCabinClass());

        List<FlightVO> flights = flightMapper.searchRecommendationFlights(
                condition.getDepartureCity(),
                condition.getArrivalCity(),
                condition.getDepartureDate(),
                resolvedAirlineId,
                condition.getMinPrice(),
                condition.getMaxPrice(),
                condition.getDepartureTimeStart(),
                condition.getDepartureTimeEnd(),
                condition.getMaxDurationMinutes(),
                condition.getDirectOnly(),
                condition.getPassengerCount(),
                condition.getCabinClass(),
                orderBy,
                DEFAULT_LIMIT
        );

        return flights.stream()
                .map(f -> toCard(f, condition.getCabinClass()))
                .toList();
    }

    public String buildSearchUrl(ParsedCondition condition) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/flights");
        if (condition.getDepartureCity() != null) {
            builder.queryParam("departureCity", condition.getDepartureCity());
        }
        if (condition.getArrivalCity() != null) {
            builder.queryParam("arrivalCity", condition.getArrivalCity());
        }
        if (condition.getDepartureDate() != null) {
            builder.queryParam("departureDate", condition.getDepartureDate().toString());
        }
        if (condition.getSort() != null) {
            builder.queryParam("sort", condition.getSort());
        }
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

    private String resolveOrderBy(String sort, String cabinClass) {
        FlightSort flightSort = FlightSort.fromParam(sort);
        if (flightSort == null) flightSort = FlightSort.DEFAULT;
        // cabinClass 经白名单校验后才拼入 ORDER BY(${}),防 SQL 注入
        boolean hasCabin = cabinClass != null && VALID_CABINS.contains(cabinClass);
        return switch (flightSort) {
            // 指定舱位时按该舱位价排序(与 FlightService.sortToOrderBy 一致);否则按 base_price
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
