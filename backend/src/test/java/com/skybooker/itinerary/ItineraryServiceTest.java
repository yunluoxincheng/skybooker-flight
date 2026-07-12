package com.skybooker.itinerary;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.flight.service.FlightService;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.mapper.ItineraryMapper;
import com.skybooker.itinerary.service.ItineraryService;
import com.skybooker.itinerary.entity.ConnectingItinerary;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.passenger.mapper.PassengerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class ItineraryServiceTest {
    private ItineraryService service;
    private ItineraryMapper itineraryMapper;
    private FlightService flightService;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 11, 10, 0);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        itineraryMapper = mock(ItineraryMapper.class);
        ConnectingItinerary published = new ConnectingItinerary();
        published.setPublishStatus("PUBLISHED");
        when(itineraryMapper.findManagedPair(1L, 2L)).thenReturn(published);
        flightService = mock(FlightService.class);
        service = new ItineraryService(itineraryMapper, mock(FlightMapper.class),
                flightService, mock(PassengerMapper.class), clock);
    }

    @Test void acceptsNinetyMinuteAndSixHourBoundaries() {
        assertDoesNotThrow(() -> service.validate(pair(90), 1));
        assertDoesNotThrow(() -> service.validate(pair(360), 1));
    }

    @Test void rejectsTransferOutsideWindow() {
        assertThrows(BusinessException.class, () -> service.validate(pair(89), 1));
        assertThrows(BusinessException.class, () -> service.validate(pair(361), 1));
    }

    @Test void rejectsDisconnectedAndDepartedSegments() {
        List<FlightVO> disconnected = pair(120);
        disconnected.get(1).setDepartureAirportId(99L);
        assertThrows(BusinessException.class, () -> service.validate(disconnected, 1));
        FlightVO departed = flight(1L, 1L, 2L, now.minusHours(2), now.minusHours(1));
        assertThrows(BusinessException.class, () -> service.validate(List.of(departed), 1));
    }

    @Test void rejectsInsufficientCapacity() {
        assertThrows(BusinessException.class, () -> service.validate(pair(120), 11));
    }

    @Test void rejectsLegacyNonDirectFlightsAsItinerarySegments() {
        List<FlightVO> flights = pair(120);
        flights.getFirst().setDirectFlag(0);
        assertThrows(BusinessException.class, () -> service.validate(flights, 1));
    }

    @Test void rejectsUnmanagedOrDraftConnectingPair() {
        when(itineraryMapper.findManagedPair(1L, 2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.validate(pair(120), 1));
        ConnectingItinerary draft = new ConnectingItinerary(); draft.setPublishStatus("DRAFT");
        when(itineraryMapper.findManagedPair(1L, 2L)).thenReturn(draft);
        assertThrows(BusinessException.class, () -> service.validate(pair(120), 1));
    }

    @Test void unifiedSearchDoesNotTruncateDirectFlightsAfterFirstHundred() {
        List<FlightVO> firstHundred = java.util.stream.LongStream.rangeClosed(1, 100)
                .mapToObj(id -> flight(id, 1L, 3L, now.plusDays(1).plusMinutes(id), now.plusDays(1).plusMinutes(id + 60)))
                .toList();
        FlightVO last = flight(101L, 1L, 3L, now.plusDays(1).plusMinutes(101), now.plusDays(1).plusMinutes(161));
        when(flightService.searchFlights(any(FlightSearchDTO.class)))
                .thenReturn(new PageResponse<>(firstHundred, 101, 1, 100))
                .thenReturn(new PageResponse<>(List.of(last), 101, 2, 100));
        when(itineraryMapper.findConnectingPairs("上海", "北京", now.toLocalDate().plusDays(1), 1, null))
                .thenReturn(List.of());
        FlightSearchDTO query = new FlightSearchDTO();
        query.setDepartureCity(" 上海 "); query.setArrivalCity(" 北京 "); query.setDepartureDate(now.toLocalDate().plusDays(1));
        query.setPage(11); query.setSize(10);
        PageResponse<com.skybooker.itinerary.vo.ItineraryVO> result = service.search(query);
        org.junit.jupiter.api.Assertions.assertEquals(101, result.getTotal());
        org.junit.jupiter.api.Assertions.assertEquals(101L, result.getRecords().getFirst().getId());
    }

    private List<FlightVO> pair(int transferMinutes) {
        FlightVO first = flight(1L, 1L, 2L, now.plusDays(1), now.plusDays(1).plusHours(2));
        FlightVO second = flight(2L, 2L, 3L, first.getArrivalTime().plusMinutes(transferMinutes), first.getArrivalTime().plusMinutes(transferMinutes + 120));
        return List.of(first, second);
    }

    private FlightVO flight(Long id, Long departureAirport, Long arrivalAirport, LocalDateTime departure, LocalDateTime arrival) {
        FlightVO f = new FlightVO(); f.setId(id); f.setDepartureAirportId(departureAirport); f.setArrivalAirportId(arrivalAirport);
        f.setDepartureTime(departure); f.setArrivalTime(arrival); f.setPublishStatus("PUBLISHED"); f.setStatus("ON_TIME");
        f.setRemainingSeats(10); f.setBasePrice(new BigDecimal("500.00")); f.setDirectFlag(1); return f;
    }
}
