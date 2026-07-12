package com.skybooker.ai;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.service.FlightRecommendationService;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.service.ItineraryService;
import com.skybooker.itinerary.vo.ItineraryVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlightRecommendationServiceTest {

    @Test
    void recommendsUnifiedDirectAndConnectingJourneysInServiceOrder() {
        ItineraryService itineraryService = mock(ItineraryService.class);
        FlightRecommendationService service = new FlightRecommendationService(itineraryService);
        ItineraryVO direct = journey(11L, "DIRECT", List.of(segment(11L, "MU101")));
        ItineraryVO connecting = journey(88L, "CONNECTING",
                List.of(segment(21L, "CZ201"), segment(22L, "CZ202")));
        when(itineraryService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(connecting, direct), 2, 1, 3));

        ParsedCondition condition = ParsedCondition.builder()
                .departureCity("上海").arrivalCity("成都").departureDate(LocalDate.of(2026, 8, 1))
                .passengerCount(2).cabinClass("BUSINESS").sort("PRICE_ASC").directOnly(false).build();
        List<Map<String, Object>> cards = service.recommend(condition, 7L);

        assertThat(cards).extracting(card -> card.get("journeyType"))
                .containsExactly("CONNECTING", "DIRECT");
        assertThat((List<?>) cards.getFirst().get("segments")).hasSize(2);
        assertThat(cards.getFirst().get("detailUrl")).isEqualTo("/itineraries/connecting/88");
        assertThat(cards.getLast().get("detailUrl")).isEqualTo("/flights/11");

        ArgumentCaptor<FlightSearchDTO> query = ArgumentCaptor.forClass(FlightSearchDTO.class);
        verify(itineraryService).search(query.capture());
        assertThat(query.getValue().getDirectOnly()).isFalse();
        assertThat(query.getValue().getPassengerCount()).isEqualTo(2);
        assertThat(query.getValue().getCabinClass()).isEqualTo("BUSINESS");
        assertThat(query.getValue().getAirlineId()).isEqualTo(7L);
        assertThat(query.getValue().getSize()).isEqualTo(3);
    }

    @Test
    void forwardsDirectOnlyToUnifiedSearch() {
        ItineraryService itineraryService = mock(ItineraryService.class);
        FlightRecommendationService service = new FlightRecommendationService(itineraryService);
        when(itineraryService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 1, 3));

        service.recommend(ParsedCondition.builder().departureCity("上海").arrivalCity("北京")
                .departureDate(LocalDate.of(2026, 8, 1)).directOnly(true).build(), null);

        ArgumentCaptor<FlightSearchDTO> query = ArgumentCaptor.forClass(FlightSearchDTO.class);
        verify(itineraryService).search(query.capture());
        assertThat(query.getValue().getDirectOnly()).isTrue();
    }

    private ItineraryVO journey(Long id, String type, List<FlightVO> segments) {
        ItineraryVO value = new ItineraryVO();
        value.setId(id);
        value.setJourneyType(type);
        value.setSegments(segments);
        value.setOriginCity("上海");
        value.setDestinationCity("成都");
        value.setTotalDurationMinutes(type.equals("CONNECTING") ? 300 : 180);
        value.setConnectionAirportCode(type.equals("CONNECTING") ? "WUH" : null);
        value.setConnectionAirportName(type.equals("CONNECTING") ? "武汉天河机场" : null);
        value.setConnectionDurationMinutes(type.equals("CONNECTING") ? 120 : null);
        value.setEstimatedAmount(new BigDecimal(type.equals("CONNECTING") ? "900" : "1000"));
        value.setAvailableSeats(4);
        value.setSellable(true);
        return value;
    }

    private FlightVO segment(Long id, String flightNo) {
        FlightVO value = new FlightVO();
        value.setId(id);
        value.setFlightNo(flightNo);
        return value;
    }
}
