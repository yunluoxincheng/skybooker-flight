package com.skybooker.itinerary.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.service.FlightService;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.dto.ItineraryQuoteDTO;
import com.skybooker.itinerary.mapper.ItineraryMapper;
import com.skybooker.itinerary.vo.ItineraryVO;
import com.skybooker.passenger.entity.Passenger;
import com.skybooker.passenger.mapper.PassengerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.skybooker.itinerary.vo.FareCalendarVO;

@Service
@RequiredArgsConstructor
public class ItineraryService {
    private final ItineraryMapper itineraryMapper;
    private final FlightMapper flightMapper;
    private final FlightService flightService;
    private final PassengerMapper passengerMapper;
    private final Clock businessClock;

    public PageResponse<ItineraryVO> search(FlightSearchDTO dto) {
        dto.setDepartureCity(trimRequired(dto.getDepartureCity()));
        dto.setArrivalCity(trimRequired(dto.getArrivalCity()));
        if (dto.getDepartureDate() == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        int page = Math.max(1, dto.getPage() == null ? 1 : dto.getPage());
        int size = Math.min(100, Math.max(1, dto.getSize() == null ? 10 : dto.getSize()));
        int passengerCount = dto.getPassengerCount() == null ? 1 : Math.max(1, dto.getPassengerCount());
        List<ItineraryVO> all = new ArrayList<>();

        FlightSearchDTO directQuery = copyForDirect(dto);
        directQuery.setPage(1);
        directQuery.setSize(100);
        flightService.searchFlights(directQuery).getRecords().stream()
                .map(f -> build(List.of(f), dto.getCabinClass())).forEach(all::add);

        if (!Boolean.TRUE.equals(dto.getDirectOnly()) && dto.getDepartureCity() != null
                && dto.getArrivalCity() != null && dto.getDepartureDate() != null) {
            itineraryMapper.findConnectingPairs(dto.getDepartureCity(), dto.getArrivalCity(), dto.getDepartureDate(),
                            passengerCount, dto.getCabinClass()).stream()
                    .map(pair -> build(pair.getItineraryId(), List.of(flightMapper.findPublishedFlightById(pair.getFirstFlightId()),
                            flightMapper.findPublishedFlightById(pair.getSecondFlightId())), dto.getCabinClass()))
                    .forEach(all::add);
        }
        all.removeIf(i -> !matchesFilters(i, dto));
        all.sort(comparator(dto.getSort()));
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResponse<>(all.subList(from, to), all.size(), page, size);
    }

    public List<FareCalendarVO> fareCalendar(String departureCity, String arrivalCity, LocalDate startDate, int days) {
        String origin = trimRequired(departureCity);
        String destination = trimRequired(arrivalCity);
        if (startDate == null || days < 1 || days > 14) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return itineraryMapper.findFareCalendar(origin, destination, startDate, startDate.plusDays(days - 1L));
    }

    private String trimRequired(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value.trim();
    }

    public ItineraryVO quote(ItineraryQuoteDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (dto.getPassengerIds().stream().distinct().count() != dto.getPassengerIds().size())
            throw new BusinessException(ErrorCode.DUPLICATE_PASSENGER_IN_ORDER);
        dto.getPassengerIds().forEach(id -> {
            Passenger p = passengerMapper.findById(id);
            if (p == null || !userId.equals(p.getUserId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        });
        List<FlightVO> flights = dto.getSegmentFlightIds().stream().map(flightMapper::findPublishedFlightById).toList();
        validate(flights, dto.getPassengerIds().size());
        if (dto.getCabinPreferences() != null && !dto.getCabinPreferences().isEmpty()) {
            if (dto.getCabinPreferences().size() != flights.size()) throw new BusinessException(ErrorCode.ITINERARY_INVALID);
            for (int i = 0; i < flights.size(); i++) {
                String cabin = dto.getCabinPreferences().get(i);
                if (cabin != null && flightMapper.countAvailableSeatsByFlightAndCabin(flights.get(i).getId(), cabin) < dto.getPassengerIds().size())
                    throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
            }
        }
        ItineraryVO result = build(flights, null);
        result.setSegmentAvailability(flights.stream().map(f -> new ItineraryVO.SegmentAvailabilityVO(
                f.getId(), flightMapper.findSeatsByFlightId(f.getId()))).toList());
        return result;
    }

    public ItineraryVO connectingDetail(Long id) {
        var managed = itineraryMapper.findManagedById(id);
        if (managed == null || !"PUBLISHED".equals(managed.getPublishStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<FlightVO> flights = List.of(flightMapper.findPublishedFlightById(managed.getFirstFlightId()),
                flightMapper.findPublishedFlightById(managed.getSecondFlightId()));
        validate(flights, 1);
        return build(id, flights, null);
    }

    public void validate(List<FlightVO> flights, int passengerCount) {
        if (flights.size() < 1 || flights.size() > 2 || flights.stream().anyMatch(f -> f == null))
            throw new BusinessException(ErrorCode.ITINERARY_INVALID);
        for (FlightVO f : flights) {
            if (!"PUBLISHED".equals(f.getPublishStatus()) || !f.getDepartureTime().isAfter(LocalDateTime.now(businessClock))
                    || !("ON_TIME".equals(f.getStatus()) || "DELAYED".equals(f.getStatus()))
                    || !Integer.valueOf(1).equals(f.getDirectFlag())
                    || f.getRemainingSeats() < passengerCount) throw new BusinessException(ErrorCode.ITINERARY_INVALID);
        }
        if (flights.size() == 2) {
            FlightVO first = flights.get(0), second = flights.get(1);
            long transfer = Duration.between(first.getArrivalTime(), second.getDepartureTime()).toMinutes();
            if (!first.getArrivalAirportId().equals(second.getDepartureAirportId()) || transfer < 90 || transfer > 360)
                throw new BusinessException(ErrorCode.INVALID_CONNECTION);
            var managed = itineraryMapper.findManagedPair(first.getId(), second.getId());
            if (managed == null || !"PUBLISHED".equals(managed.getPublishStatus())) {
                throw new BusinessException(ErrorCode.ITINERARY_INVALID);
            }
        }
    }

    private ItineraryVO build(List<FlightVO> flights, String cabinClass) {
        return build(flights.size() == 1 ? flights.getFirst().getId() : null, flights, cabinClass);
    }

    private ItineraryVO build(Long id, List<FlightVO> flights, String cabinClass) {
        FlightVO first = flights.getFirst(), last = flights.getLast();
        int connection = flights.size() == 2 ? (int) Duration.between(first.getArrivalTime(), last.getDepartureTime()).toMinutes() : 0;
        int duration = (int) Duration.between(first.getDepartureTime(), last.getArrivalTime()).toMinutes();
        BigDecimal price = flights.stream().map(f -> cabinPrice(f, cabinClass)).reduce(BigDecimal.ZERO, BigDecimal::add);
        int seats = flights.stream().mapToInt(FlightVO::getRemainingSeats).min().orElse(0);
        return new ItineraryVO(id, flights.size() == 2 ? "CONNECTING" : "DIRECT", flights, first.getDepartureCity(),
                last.getArrivalCity(), flights.size() == 2 ? first.getArrivalAirportCode() : null,
                flights.size() == 2 ? first.getArrivalAirportName() : null, flights.size() == 2 ? connection : null,
                duration, price, seats, seats > 0, null);
    }

    private BigDecimal cabinPrice(FlightVO flight, String cabinClass) {
        if (cabinClass != null && flight.getCabins() != null) {
            return flight.getCabins().stream().filter(c -> cabinClass.equals(c.getCabinClass()))
                    .map(c -> c.getPrice()).findFirst().orElse(flight.getBasePrice());
        }
        return flight.getBasePrice();
    }

    private boolean matchesFilters(ItineraryVO i, FlightSearchDTO d) {
        FlightVO first = i.getSegments().getFirst();
        if (d.getAirlineId() != null && i.getSegments().stream().noneMatch(f -> d.getAirlineId().equals(f.getAirlineId()))) return false;
        if (d.getMinPrice() != null && i.getEstimatedAmount().compareTo(d.getMinPrice()) < 0) return false;
        if (d.getMaxPrice() != null && i.getEstimatedAmount().compareTo(d.getMaxPrice()) > 0) return false;
        if (d.getDepartureTimeStart() != null && first.getDepartureTime().toLocalTime().isBefore(d.getDepartureTimeStart())) return false;
        if (d.getDepartureTimeEnd() != null && first.getDepartureTime().toLocalTime().isAfter(d.getDepartureTimeEnd())) return false;
        if (d.getMaxDurationMinutes() != null && i.getTotalDurationMinutes() > d.getMaxDurationMinutes()) return false;
        return d.getStatus() == null || i.getSegments().stream().allMatch(f -> d.getStatus().equals(f.getStatus()));
    }

    private Comparator<ItineraryVO> comparator(String sort) {
        if (sort == null) return Comparator.comparing(i -> i.getSegments().getFirst().getDepartureTime());
        return switch (sort.toUpperCase()) {
            case "PRICE_ASC" -> Comparator.comparing(ItineraryVO::getEstimatedAmount);
            case "DURATION_ASC" -> Comparator.comparing(ItineraryVO::getTotalDurationMinutes);
            case "SEATS_DESC" -> Comparator.comparing(ItineraryVO::getAvailableSeats).reversed();
            default -> Comparator.comparing(i -> i.getSegments().getFirst().getDepartureTime());
        };
    }

    private FlightSearchDTO copyForDirect(FlightSearchDTO source) {
        FlightSearchDTO d = new FlightSearchDTO();
        d.setFlightNo(source.getFlightNo()); d.setDepartureCity(source.getDepartureCity()); d.setArrivalCity(source.getArrivalCity());
        d.setDepartureDate(source.getDepartureDate()); d.setDepartureDateStart(source.getDepartureDateStart()); d.setDepartureDateEnd(source.getDepartureDateEnd());
        d.setAirlineId(source.getAirlineId()); d.setMinPrice(source.getMinPrice()); d.setMaxPrice(source.getMaxPrice());
        d.setDepartureTimeStart(source.getDepartureTimeStart()); d.setDepartureTimeEnd(source.getDepartureTimeEnd());
        d.setMaxDurationMinutes(source.getMaxDurationMinutes()); d.setDirectOnly(true); d.setStatus(source.getStatus());
        d.setSort(source.getSort()); d.setPassengerCount(source.getPassengerCount()); d.setCabinClass(source.getCabinClass()); d.setIncludeSoldOut(false);
        return d;
    }
}
