package com.skybooker.itinerary;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.flight.service.FlightService;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.mapper.ItineraryMapper;
import com.skybooker.itinerary.service.ItineraryService;
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

class ItineraryServiceTest {
    private ItineraryService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 11, 10, 0);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ItineraryService(mock(ItineraryMapper.class), mock(FlightMapper.class),
                mock(FlightService.class), mock(PassengerMapper.class), clock);
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

    private List<FlightVO> pair(int transferMinutes) {
        FlightVO first = flight(1L, 1L, 2L, now.plusDays(1), now.plusDays(1).plusHours(2));
        FlightVO second = flight(2L, 2L, 3L, first.getArrivalTime().plusMinutes(transferMinutes), first.getArrivalTime().plusMinutes(transferMinutes + 120));
        return List.of(first, second);
    }

    private FlightVO flight(Long id, Long departureAirport, Long arrivalAirport, LocalDateTime departure, LocalDateTime arrival) {
        FlightVO f = new FlightVO(); f.setId(id); f.setDepartureAirportId(departureAirport); f.setArrivalAirportId(arrivalAirport);
        f.setDepartureTime(departure); f.setArrivalTime(arrival); f.setPublishStatus("PUBLISHED"); f.setStatus("ON_TIME");
        f.setRemainingSeats(10); f.setBasePrice(new BigDecimal("500.00")); return f;
    }
}
