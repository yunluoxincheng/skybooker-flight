package com.skybooker.change.service;

import com.skybooker.change.entity.ChangeRecord;
import com.skybooker.change.mapper.ChangeMapper;
import com.skybooker.change.vo.ChangeOptionVO;
import com.skybooker.change.vo.ChangeOrderResultVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.order.dto.ChangeOrderDTO;
import com.skybooker.order.entity.OrderPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChangeService {

    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;
    private final ChangeMapper changeMapper;

    private static final BigDecimal AIRPORT_FEE_PER_PASSENGER = new BigDecimal("50.00");
    private static final BigDecimal FUEL_FEE_PER_PASSENGER = new BigDecimal("30.00");
    private static final BigDecimal SERVICE_FEE = BigDecimal.ZERO;

    public List<ChangeOptionVO> listChangeOptions(Long orderId) {
        Long userId = SecurityUtil.getCurrentUserId();
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"ISSUED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        Flight currentFlight = flightMapper.findById(order.getFlightId());
        validateChangeCutoff(currentFlight);

        List<Flight> candidates = flightMapper.findSameRouteFlights(
                currentFlight.getDepartureAirportId(),
                currentFlight.getArrivalAirportId(),
                currentFlight.getId(),
                countOrderPassengers(orderId));

        return candidates.stream().map(f -> new ChangeOptionVO(
                f.getId(),
                f.getFlightNo(),
                f.getDepartureTime(),
                f.getArrivalTime(),
                f.getBasePrice(),
                f.getRemainingSeats(),
                f.getStatus()
        )).collect(Collectors.toList());
    }

    @Transactional
    public ChangeOrderResultVO changeOrder(Long orderId, ChangeOrderDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("CHANGED".equals(order.getStatus())) {
            OrderVO detail = orderMapper.findDetailById(orderId);
            return new ChangeOrderResultVO(
                    detail.getId(), detail.getOrderNo(), detail.getStatus(),
                    detail.getFlightId(), detail.getTotalAmount(), detail.getPassengers());
        }
        if (!"ISSUED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        Flight currentFlight = flightMapper.findById(order.getFlightId());
        validateChangeCutoff(currentFlight);

        if (dto.getNewFlightId().equals(order.getFlightId())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        Flight newFlight = flightMapper.findById(dto.getNewFlightId());
        if (newFlight == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        validateFlightSellability(newFlight);
        if (!newFlight.getDepartureAirportId().equals(currentFlight.getDepartureAirportId())
                || !newFlight.getArrivalAirportId().equals(currentFlight.getArrivalAirportId())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        List<OrderPassenger> currentPassengers = orderMapper.findPassengersByOrderId(orderId);
        validatePassengerMappings(dto.getSeatMappings(), currentPassengers);
        Map<Long, ChangeOrderDTO.SeatMapping> mappingByPassengerId = dto.getSeatMappings().stream()
                .collect(Collectors.toMap(ChangeOrderDTO.SeatMapping::getPassengerId, mapping -> mapping));

        List<Long> newSeatIds = dto.getSeatMappings().stream()
                .map(ChangeOrderDTO.SeatMapping::getNewSeatId).toList();
        List<FlightSeat> newSeats = validateNewSeats(newSeatIds, dto.getNewFlightId());
        Map<Long, FlightSeat> newSeatById = newSeats.stream()
                .collect(Collectors.toMap(FlightSeat::getId, seat -> seat));

        BigDecimal changeFeeRate = calculateChangeFeeRate(currentFlight);
        BigDecimal changeFee = order.getTotalAmount().multiply(changeFeeRate)
                .setScale(2, RoundingMode.HALF_UP);

        int cas = orderMapper.updateOrderStatusCAS(orderId, "ISSUED", "CHANGED");
        if (cas == 0) {
            OrderVO detail = orderMapper.findDetailById(orderId);
            return new ChangeOrderResultVO(
                    detail.getId(), detail.getOrderNo(), detail.getStatus(),
                    detail.getFlightId(), detail.getTotalAmount(), detail.getPassengers());
        }

        List<Long> oldSeatIds = currentPassengers.stream()
                .map(OrderPassenger::getSeatId).toList();

        int released = flightMapper.releaseSoldSeatsBySeatIds(oldSeatIds);
        if (released != oldSeatIds.size()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        int sold = flightMapper.sellAvailableSeatsByIds(newSeatIds, orderId);
        if (sold != newSeatIds.size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        int passengerCount = currentPassengers.size();
        flightMapper.incrementRemainingSeats(order.getFlightId(), passengerCount);
        int decremented = flightMapper.decrementRemainingSeats(dto.getNewFlightId(), passengerCount);
        if (decremented == 0) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }

        BigDecimal newTicketAmount = newSeats.stream()
                .map(FlightSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal airportFee = AIRPORT_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal fuelFee = FUEL_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal newTotalAmount = newTicketAmount.add(airportFee).add(fuelFee).add(SERVICE_FEE);

        orderMapper.updateOrderFlightAndAmounts(orderId, dto.getNewFlightId(),
                newTicketAmount, airportFee, fuelFee, SERVICE_FEE, newTotalAmount);

        for (int i = 0; i < currentPassengers.size(); i++) {
            OrderPassenger op = currentPassengers.get(i);
            ChangeOrderDTO.SeatMapping mapping = mappingByPassengerId.get(op.getPassengerId());
            FlightSeat newSeat = newSeatById.get(mapping.getNewSeatId());

            orderMapper.updateOrderPassengerSeat(op.getId(),
                    mapping.getNewSeatId(), newSeat.getSeatNo(), newSeat.getPrice());

            BigDecimal priceDiff = newSeat.getPrice().subtract(op.getTicketPrice())
                    .setScale(2, RoundingMode.HALF_UP);

            ChangeRecord record = new ChangeRecord();
            record.setOrderId(orderId);
            record.setOldFlightId(order.getFlightId());
            record.setNewFlightId(dto.getNewFlightId());
            record.setOldSeatId(op.getSeatId());
            record.setNewSeatId(mapping.getNewSeatId());
            record.setPriceDiff(priceDiff);
            record.setChangeFee(changeFee);
            record.setStatus("SUCCESS");
            changeMapper.insert(record);
        }

        OrderVO detail = orderMapper.findDetailById(orderId);
        return new ChangeOrderResultVO(
                detail.getId(), detail.getOrderNo(), detail.getStatus(),
                detail.getFlightId(), detail.getTotalAmount(), detail.getPassengers());
    }

    private void validateChangeCutoff(Flight flight) {
        Duration remaining = Duration.between(LocalDateTime.now(), flight.getDepartureTime());
        if (remaining.compareTo(Duration.ofHours(2)) < 0) {
            throw new BusinessException(ErrorCode.CHANGE_WINDOW_CLOSED);
        }
    }

    private void validateFlightSellability(Flight flight) {
        if (!"PUBLISHED".equals(flight.getPublishStatus())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        if (flight.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        if (!"ON_TIME".equals(flight.getStatus()) && !"DELAYED".equals(flight.getStatus())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
    }

    private void validatePassengerMappings(List<ChangeOrderDTO.SeatMapping> mappings,
                                           List<OrderPassenger> passengers) {
        if (mappings.size() != passengers.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Set<Long> passengerIds = passengers.stream()
                .map(OrderPassenger::getPassengerId).collect(Collectors.toSet());
        Set<Long> mappingPassengerIds = mappings.stream()
                .map(ChangeOrderDTO.SeatMapping::getPassengerId).collect(Collectors.toSet());
        if (!passengerIds.equals(mappingPassengerIds)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Set<Long> newSeatIds = new HashSet<>();
        for (ChangeOrderDTO.SeatMapping mapping : mappings) {
            if (!newSeatIds.add(mapping.getNewSeatId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_ORDER);
            }
        }
    }

    private List<FlightSeat> validateNewSeats(List<Long> seatIds, Long flightId) {
        List<FlightSeat> seats = new ArrayList<>();
        for (Long seatId : seatIds) {
            FlightSeat seat = flightMapper.findSeatById(seatId);
            if (seat == null || !seat.getFlightId().equals(flightId)) {
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            if (!"AVAILABLE".equals(seat.getStatus())) {
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            seats.add(seat);
        }
        return seats;
    }

    private BigDecimal calculateChangeFeeRate(Flight flight) {
        Duration remaining = Duration.between(LocalDateTime.now(), flight.getDepartureTime());
        if (remaining.compareTo(Duration.ofHours(24)) > 0) {
            return new BigDecimal("0.10");
        }
        return new BigDecimal("0.30");
    }

    private int countOrderPassengers(Long orderId) {
        OrderVO detail = orderMapper.findDetailById(orderId);
        return detail != null && detail.getPassengers() != null ? detail.getPassengers().size() : 0;
    }
}
