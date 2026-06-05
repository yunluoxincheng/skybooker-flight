package com.skybooker.ai.service;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.flight.enums.FlightSort;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlightRecommendationService {

    private static final int DEFAULT_LIMIT = 3;

    private final FlightMapper flightMapper;

    public List<Map<String, Object>> recommend(ParsedCondition condition, Long resolvedAirlineId) {
        String orderBy = resolveOrderBy(condition.getSort());

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
        StringBuilder sb = new StringBuilder("/flights?");
        if (condition.getDepartureCity() != null) {
            sb.append("departureCity=").append(urlEncode(condition.getDepartureCity())).append("&");
        }
        if (condition.getArrivalCity() != null) {
            sb.append("arrivalCity=").append(urlEncode(condition.getArrivalCity())).append("&");
        }
        if (condition.getDepartureDate() != null) {
            sb.append("departureDate=").append(condition.getDepartureDate()).append("&");
        }
        if (condition.getSort() != null) {
            sb.append("sort=").append(condition.getSort()).append("&");
        }
        return sb.toString();
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

    private String resolveOrderBy(String sort) {
        FlightSort flightSort = FlightSort.fromParam(sort);
        if (flightSort == null) flightSort = FlightSort.DEFAULT;
        return switch (flightSort) {
            case PRICE_ASC -> "f.base_price ASC, f.departure_time ASC";
            case DURATION_ASC -> "f.duration_minutes ASC, f.departure_time ASC";
            case TIME_ASC -> "f.departure_time ASC";
            case SEATS_DESC -> "f.remaining_seats DESC, f.departure_time ASC";
            case PUNCTUAL_DESC -> "f.punctuality_rate DESC, f.departure_time ASC";
            default -> "f.departure_time ASC";
        };
    }

    private String urlEncode(String value) {
        return value.replace(" ", "%20");
    }
}
