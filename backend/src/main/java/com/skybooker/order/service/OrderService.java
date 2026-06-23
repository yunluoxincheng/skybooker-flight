package com.skybooker.order.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.order.dto.CreateOrderDTO;
import com.skybooker.order.entity.OrderPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.passenger.entity.Passenger;
import com.skybooker.passenger.mapper.PassengerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;
    private final PassengerMapper passengerMapper;
    private final OrderCleanupService cleanupService;

    private static final BigDecimal AIRPORT_FEE_PER_PASSENGER = new BigDecimal("50.00");
    private static final BigDecimal FUEL_FEE_PER_PASSENGER = new BigDecimal("30.00");
    private static final BigDecimal SERVICE_FEE = BigDecimal.ZERO;
    private static final int ORDER_EXPIRY_MINUTES = 15;

    @Transactional
    public OrderVO createOrder(CreateOrderDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<CreateOrderDTO.OrderItemDTO> items = dto.getItems();
        int passengerCount = items.size();

        validateItems(items);
        cleanupService.cleanupAllExpiredOrders();

        List<Passenger> passengers = validatePassengerOwnership(items, userId);
        Flight flight = validateFlightSellability(dto.getFlightId(), passengerCount);
        List<FlightSeat> seats = validateSeats(items, dto.getFlightId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(ORDER_EXPIRY_MINUTES);

        BigDecimal ticketAmount = seats.stream().map(FlightSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal airportFee = AIRPORT_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal fuelFee = FUEL_FEE_PER_PASSENGER.multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal totalAmount = ticketAmount.add(airportFee).add(fuelFee).add(SERVICE_FEE);

        TicketOrder order = new TicketOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setFlightId(flight.getId());
        order.setStatus("PENDING_PAYMENT");
        order.setTicketAmount(ticketAmount);
        order.setAirportFee(airportFee);
        order.setFuelFee(fuelFee);
        order.setServiceFee(SERVICE_FEE);
        order.setTotalAmount(totalAmount);
        order.setExpireTime(expireTime);
        orderMapper.insertOrder(order);

        List<Long> seatIds = seats.stream().map(FlightSeat::getId).sorted().toList();
        int locked = flightMapper.lockSeats(seatIds, order.getId(), expireTime);
        if (locked != passengerCount) {
            throw new BusinessException(ErrorCode.SEAT_LOCK_FAILED);
        }

        int decremented = flightMapper.decrementRemainingSeats(flight.getId(), passengerCount);
        if (decremented == 0) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }

        List<OrderPassenger> orderPassengers = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OrderPassenger op = new OrderPassenger();
            op.setOrderId(order.getId());
            op.setPassengerId(passengers.get(i).getId());
            op.setPassengerName(passengers.get(i).getName());
            op.setPassengerType(passengers.get(i).getPassengerType());
            op.setSeatId(seats.get(i).getId());
            op.setSeatNo(seats.get(i).getSeatNo());
            op.setTicketPrice(seats.get(i).getPrice());
            orderPassengers.add(op);
        }
        orderMapper.batchInsertOrderPassengers(orderPassengers);

        return getOrderDetailForUser(order.getId(), userId);
    }

    @Transactional
    public OrderVO payOrder(Long orderId) {
        Long userId = SecurityUtil.getCurrentUserId();
        cleanupService.cleanupExpiredOrder(orderId);
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("ISSUED".equals(order.getStatus())) {
            return getOrderDetailForUser(orderId, userId);
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED);
        }

        int cas = orderMapper.updateOrderStatusCAS(orderId, "PENDING_PAYMENT", "ISSUED");
        if (cas == 0) {
            return getOrderDetailForUser(orderId, userId);
        }
        int passengerCount = countOrderPassengers(orderId);
        int sold = flightMapper.updateSeatStatusToSold(orderId);
        if (sold != passengerCount) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        orderMapper.updatePayTime(orderId, LocalDateTime.now());

        return getOrderDetailForUser(orderId, userId);
    }

    public PageResponse<OrderVO> listMyOrders(int page, int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        cleanupService.cleanupAllExpiredOrders();
        int offset = (page - 1) * size;
        List<OrderVO> records = orderMapper.findByUserId(userId, offset, size);
        long total = orderMapper.countByUserId(userId);
        return new PageResponse<>(records, total, page, size);
    }

    public OrderVO getOrderDetail(Long orderId) {
        Long userId = SecurityUtil.getCurrentUserId();
        cleanupService.cleanupExpiredOrder(orderId);
        return getOrderDetailForUser(orderId, userId);
    }

    @Transactional
    public OrderVO cancelOrder(Long orderId) {
        Long userId = SecurityUtil.getCurrentUserId();
        cleanupService.cleanupExpiredOrder(orderId);
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return getOrderDetailForUser(orderId, userId);
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        int cas = orderMapper.updateOrderStatusCAS(orderId, "PENDING_PAYMENT", "CANCELLED");
        if (cas == 0) {
            return getOrderDetailForUser(orderId, userId);
        }
        int passengerCount = countOrderPassengers(orderId);
        int released = flightMapper.releaseSeatsByOrderId(orderId);
        if (released == passengerCount) {
            flightMapper.incrementRemainingSeats(order.getFlightId(), passengerCount);
        }

        return getOrderDetailForUser(orderId, userId);
    }

    private void validateItems(List<CreateOrderDTO.OrderItemDTO> items) {
        Set<Long> passengerIds = new HashSet<>();
        Set<Long> seatIds = new HashSet<>();
        for (CreateOrderDTO.OrderItemDTO item : items) {
            if (!passengerIds.add(item.getPassengerId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_PASSENGER_IN_ORDER);
            }
            if (!seatIds.add(item.getSeatId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_ORDER);
            }
        }
    }

    private List<Passenger> validatePassengerOwnership(List<CreateOrderDTO.OrderItemDTO> items, Long userId) {
        List<Passenger> passengers = new ArrayList<>();
        for (CreateOrderDTO.OrderItemDTO item : items) {
            Passenger p = passengerMapper.findById(item.getPassengerId());
            if (p == null || !p.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            passengers.add(p);
        }
        return passengers;
    }

    private Flight validateFlightSellability(Long flightId, int passengerCount) {
        Flight flight = flightMapper.findById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"PUBLISHED".equals(flight.getPublishStatus())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        if (flight.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        if (!"ON_TIME".equals(flight.getStatus()) && !"DELAYED".equals(flight.getStatus())) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        if (flight.getRemainingSeats() < passengerCount) {
            throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
        }
        return flight;
    }

    private List<FlightSeat> validateSeats(List<CreateOrderDTO.OrderItemDTO> items, Long flightId) {
        List<FlightSeat> seats = new ArrayList<>();
        for (CreateOrderDTO.OrderItemDTO item : items) {
            FlightSeat seat = flightMapper.findSeatById(item.getSeatId());
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

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
    }

    private int countOrderPassengers(Long orderId) {
        OrderVO detail = orderMapper.findDetailById(orderId);
        return detail != null && detail.getPassengers() != null ? detail.getPassengers().size() : 0;
    }

    private OrderVO getOrderDetailForUser(Long orderId, Long userId) {
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return orderMapper.findDetailById(orderId);
    }
}
