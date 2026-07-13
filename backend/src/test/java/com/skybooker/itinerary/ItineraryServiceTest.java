package com.skybooker.itinerary;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.flight.service.FlightService;
import com.skybooker.flight.service.CityQueryService;
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
    private FlightMapper flightMapper;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 11, 10, 0);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        itineraryMapper = mock(ItineraryMapper.class);
        ConnectingItinerary published = new ConnectingItinerary();
        published.setPublishStatus("PUBLISHED");
        when(itineraryMapper.findManagedPair(1L, 2L)).thenReturn(published);
        flightService = mock(FlightService.class);
        flightMapper = mock(FlightMapper.class);
        service = new ItineraryService(itineraryMapper, flightMapper,
                flightService, new CityQueryService(), mock(PassengerMapper.class), clock);
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
        when(itineraryMapper.findConnectingPairs(List.of("上海", "上海市"), List.of("北京", "北京市"),
                now.toLocalDate().plusDays(1), 1, null))
                .thenReturn(List.of());
        FlightSearchDTO query = new FlightSearchDTO();
        query.setDepartureCity(" 上海 "); query.setArrivalCity(" 北京 "); query.setDepartureDate(now.toLocalDate().plusDays(1));
        query.setPage(11); query.setSize(10);
        PageResponse<com.skybooker.itinerary.vo.ItineraryVO> result = service.search(query);
        org.junit.jupiter.api.Assertions.assertEquals(101, result.getTotal());
        org.junit.jupiter.api.Assertions.assertEquals(101L, result.getRecords().getFirst().getId());
    }

    @Test void defaultSearchPassesOnePassengerAndCabinAvailabilityControlsSeats() {
        FlightVO direct = flight(1L, 1L, 3L, now.plusDays(1), now.plusDays(1).plusHours(1));
        com.skybooker.flight.vo.FlightCabinVO business = new com.skybooker.flight.vo.FlightCabinVO();
        business.setCabinClass("BUSINESS"); business.setPrice(new BigDecimal("888.00"));
        direct.setCabins(List.of(business));
        when(flightService.searchFlights(any(FlightSearchDTO.class)))
                .thenReturn(new PageResponse<>(List.of(direct), 1, 1, 100));
        when(flightMapper.countAvailableSeatsByFlightAndCabin(1L, "BUSINESS")).thenReturn(2);
        when(itineraryMapper.findConnectingPairs(List.of("上海", "上海市"), List.of("北京", "北京市"),
                now.toLocalDate().plusDays(1), 1, "BUSINESS"))
                .thenReturn(List.of());
        FlightSearchDTO query = new FlightSearchDTO(); query.setDepartureCity("上海"); query.setArrivalCity("北京");
        query.setDepartureDate(now.toLocalDate().plusDays(1)); query.setCabinClass("BUSINESS");
        var result = service.search(query);
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("888.00"), result.getRecords().getFirst().getEstimatedAmount());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.getRecords().getFirst().getAvailableSeats());
        org.mockito.ArgumentCaptor<FlightSearchDTO> captor = org.mockito.ArgumentCaptor.forClass(FlightSearchDTO.class);
        org.mockito.Mockito.verify(flightService).searchFlights(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, captor.getValue().getPassengerCount());
    }

    @Test void publishedConnectingDetailReturnsSoldOutReasonInsteadOfFailing() {
        ConnectingItinerary managed = new ConnectingItinerary(); managed.setId(9L); managed.setPublishStatus("PUBLISHED");
        managed.setFirstFlightId(1L); managed.setSecondFlightId(2L);
        when(itineraryMapper.findManagedById(9L)).thenReturn(managed);
        List<FlightVO> pair = pair(120); pair.getLast().setRemainingSeats(0);
        when(flightMapper.findFlightByIdAnyStatus(1L)).thenReturn(pair.getFirst());
        when(flightMapper.findFlightByIdAnyStatus(2L)).thenReturn(pair.getLast());
        var detail = service.connectingDetail(9L);
        org.junit.jupiter.api.Assertions.assertFalse(detail.getSellable());
        org.junit.jupiter.api.Assertions.assertEquals("该联程行程已售罄", detail.getUnavailableReason());
    }

    @Test void publishedConnectingDetailReturnsUnpublishedReason() {
        stubManagedDetail();
        List<FlightVO> flights = pair(120); flights.getFirst().setPublishStatus("DRAFT");
        stubDetailFlights(flights);
        var detail = service.connectingDetail(9L);
        org.junit.jupiter.api.Assertions.assertFalse(detail.getSellable());
        org.junit.jupiter.api.Assertions.assertEquals("部分航段已下架", detail.getUnavailableReason());
    }

    @Test void publishedConnectingDetailReturnsControlledErrorWhenSegmentIsMissing() {
        stubManagedDetail();
        when(flightMapper.findFlightByIdAnyStatus(1L)).thenReturn(pair(120).getFirst());
        when(flightMapper.findFlightByIdAnyStatus(2L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.connectingDetail(9L));
        org.junit.jupiter.api.Assertions.assertEquals(com.skybooker.common.exception.ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
    }

    @Test void publishedConnectingDetailReturnsCancelledAndExpiredReasons() {
        stubManagedDetail();
        List<FlightVO> cancelled = pair(120); cancelled.getLast().setStatus("CANCELLED"); stubDetailFlights(cancelled);
        org.junit.jupiter.api.Assertions.assertEquals("部分航段已取消", service.connectingDetail(9L).getUnavailableReason());
        List<FlightVO> expired = pair(120);
        expired.getFirst().setDepartureTime(now.minusHours(3)); expired.getFirst().setArrivalTime(now.minusHours(1));
        expired.getLast().setDepartureTime(now.plusMinutes(119)); expired.getLast().setArrivalTime(now.plusHours(2));
        stubDetailFlights(expired);
        org.junit.jupiter.api.Assertions.assertEquals("行程已经起飞或过期", service.connectingDetail(9L).getUnavailableReason());
    }

    private void stubManagedDetail() {
        ConnectingItinerary managed = new ConnectingItinerary(); managed.setId(9L); managed.setPublishStatus("PUBLISHED");
        managed.setFirstFlightId(1L); managed.setSecondFlightId(2L);
        when(itineraryMapper.findManagedById(9L)).thenReturn(managed);
    }

    private void stubDetailFlights(List<FlightVO> flights) {
        when(flightMapper.findFlightByIdAnyStatus(1L)).thenReturn(flights.getFirst());
        when(flightMapper.findFlightByIdAnyStatus(2L)).thenReturn(flights.getLast());
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
