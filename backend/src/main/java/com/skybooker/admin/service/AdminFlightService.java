package com.skybooker.admin.service;

import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminFlightService {

    private static final Set<String> VALID_STATUSES = Set.of("ON_TIME", "DELAYED", "CANCELLED");
    private static final Set<String> VALID_PUBLISH_STATUSES = Set.of("PUBLISHED", "DRAFT");

    private final FlightMapper flightMapper;

    public PageResponse<FlightVO> listFlights(int page, int size) {
        int offset = (page - 1) * size;
        List<FlightVO> records = flightMapper.searchAllFlights(offset, size);
        long total = flightMapper.countAllFlights();
        return new PageResponse<>(records, total, page, size);
    }

    @Transactional
    public FlightVO createFlight(FlightFormDTO dto) {
        validateFlightForm(dto);
        Flight flight = toEntity(dto);
        flight.setRemainingSeats(0);
        flightMapper.insertFlight(flight);
        return flightMapper.findFlightByIdAnyStatus(flight.getId());
    }

    @Transactional
    public FlightVO updateFlight(Long id, FlightFormDTO dto) {
        Flight existing = flightMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        boolean hasSeats = flightMapper.existsSeatsByFlightId(id);
        boolean hasOrders = flightMapper.existsOrdersByFlightId(id);

        if (hasSeats || hasOrders) {
            boolean structuralChange = !existing.getFlightNo().equals(dto.getFlightNo())
                    || !existing.getAirlineId().equals(dto.getAirlineId())
                    || !existing.getDepartureAirportId().equals(dto.getDepartureAirportId())
                    || !existing.getArrivalAirportId().equals(dto.getArrivalAirportId())
                    || !existing.getDepartureTime().equals(dto.getDepartureTime())
                    || !existing.getArrivalTime().equals(dto.getArrivalTime())
                    || !existing.getTotalSeats().equals(dto.getTotalSeats())
                    || existing.getBasePrice().compareTo(dto.getBasePrice()) != 0;
            if (structuralChange) {
                throw new BusinessException(ErrorCode.FLIGHT_HAS_INVENTORY);
            }
        }

        validateFlightForm(dto);
        Flight updated = toEntity(dto);
        updated.setId(id);
        updated.setRemainingSeats(existing.getRemainingSeats());
        flightMapper.updateFlight(updated);
        return flightMapper.findFlightByIdAnyStatus(id);
    }

    @Transactional
    public void publishFlight(Long id) {
        Flight flight = flightMapper.findById(id);
        if (flight == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        flight.setPublishStatus("PUBLISHED");
        flightMapper.updateFlight(flight);
    }

    @Transactional
    public void unpublishFlight(Long id) {
        Flight flight = flightMapper.findById(id);
        if (flight == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        flight.setPublishStatus("DRAFT");
        flightMapper.updateFlight(flight);
    }

    @Transactional
    public void generateSeats(Long flightId) {
        Flight flight = flightMapper.findById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (flight.getTotalSeats() <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (flight.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (flightMapper.existsSeatsByFlightId(flightId)) throw new BusinessException(ErrorCode.SEAT_ALREADY_EXISTS);

        String[] letters = {"A", "B", "C", "D", "E", "F"};
        int totalSeats = flight.getTotalSeats();
        List<FlightSeat> seats = new ArrayList<>();
        int seatCount = 0;
        for (int row = 1; seatCount < totalSeats; row++) {
            for (String letter : letters) {
                if (seatCount >= totalSeats) break;
                FlightSeat seat = new FlightSeat();
                seat.setFlightId(flightId);
                seat.setSeatNo(row + letter);
                seat.setCabinClass("ECONOMY");
                seat.setSeatType(getSeatType(letter));
                seat.setPrice(flight.getBasePrice());
                seat.setStatus("AVAILABLE");
                seats.add(seat);
                seatCount++;
            }
        }

        flightMapper.batchInsertFlightSeats(seats);
        flightMapper.setRemainingSeats(flightId, seatCount);
    }

    private String getSeatType(String letter) {
        return switch (letter) {
            case "A", "F" -> "WINDOW";
            case "C", "D" -> "AISLE";
            default -> "NORMAL";
        };
    }

    private void validateFlightForm(FlightFormDTO dto) {
        if (!flightMapper.existsAirlineById(dto.getAirlineId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!flightMapper.existsAirportById(dto.getDepartureAirportId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!flightMapper.existsAirportById(dto.getArrivalAirportId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getDepartureAirportId().equals(dto.getArrivalAirportId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!dto.getArrivalTime().isAfter(dto.getDepartureTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String status = dto.getStatus() != null ? dto.getStatus() : "ON_TIME";
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String publishStatus = dto.getPublishStatus() != null ? dto.getPublishStatus() : "DRAFT";
        if (!VALID_PUBLISH_STATUSES.contains(publishStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getDurationMinutes() == null || dto.getDurationMinutes() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getBasePrice() == null || dto.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getTotalSeats() == null || dto.getTotalSeats() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private Flight toEntity(FlightFormDTO dto) {
        Flight f = new Flight();
        f.setFlightNo(dto.getFlightNo());
        f.setAirlineId(dto.getAirlineId());
        f.setDepartureAirportId(dto.getDepartureAirportId());
        f.setArrivalAirportId(dto.getArrivalAirportId());
        f.setDepartureTime(dto.getDepartureTime());
        f.setArrivalTime(dto.getArrivalTime());
        f.setDurationMinutes(dto.getDurationMinutes());
        f.setBasePrice(dto.getBasePrice());
        f.setTotalSeats(dto.getTotalSeats());
        f.setStatus(dto.getStatus() != null ? dto.getStatus() : "ON_TIME");
        f.setPublishStatus(dto.getPublishStatus() != null ? dto.getPublishStatus() : "DRAFT");
        f.setDirectFlag(dto.getDirectFlag() != null ? dto.getDirectFlag() : true);
        f.setBaggageAllowance(dto.getBaggageAllowance());
        f.setPunctualityRate(dto.getPunctualityRate());
        return f;
    }
}
