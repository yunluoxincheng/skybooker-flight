package com.skybooker.order.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.service.ItineraryService;
import com.skybooker.order.dto.CreateConnectingOrderDTO;
import com.skybooker.order.entity.OrderPassenger;
import com.skybooker.order.entity.OrderSegmentPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.entity.TicketOrderSegment;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.passenger.entity.Passenger;
import com.skybooker.passenger.mapper.PassengerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConnectingOrderService {
    private static final BigDecimal AIRPORT_FEE = new BigDecimal("50.00");
    private static final BigDecimal FUEL_FEE = new BigDecimal("30.00");
    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;
    private final PassengerMapper passengerMapper;
    private final ItineraryService itineraryService;
    private final OrderService orderService;
    private final Clock businessClock;

    @Transactional
    public OrderVO create(CreateConnectingOrderDTO dto) {
        return createForUser(SecurityUtil.getCurrentUserId(), dto);
    }

    @Transactional
    public OrderVO createForUser(Long userId, CreateConnectingOrderDTO dto) {
        String requestId = dto.getClientRequestId().toString();
        orderMapper.lockUserForIdempotency(userId);
        TicketOrder existing = orderMapper.findByUserAndClientRequestId(userId, requestId);
        if (existing != null) {
            if (!matches(existing.getId(), dto)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return orderService.getOrderDetailForUser(existing.getId(), userId);
        }

        Set<Long> expectedPassengers = passengerSet(dto.getSegments().getFirst());
        if (expectedPassengers.isEmpty() || !expectedPassengers.equals(passengerSet(dto.getSegments().getLast())))
            throw new BusinessException(ErrorCode.INCOMPLETE_SEGMENT_SEATS);
        Map<Long, Passenger> passengers = new HashMap<>();
        for (Long id : expectedPassengers) {
            Passenger p = passengerMapper.findById(id);
            if (p == null || !userId.equals(p.getUserId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            passengers.put(id, p);
        }

        List<FlightVO> flights = dto.getSegments().stream().map(s -> flightMapper.findPublishedFlightById(s.getFlightId())).toList();
        itineraryService.validate(flights, expectedPassengers.size());
        List<List<FlightSeat>> seatsBySegment = new ArrayList<>();
        BigDecimal ticketAmount = BigDecimal.ZERO;
        for (int i = 0; i < 2; i++) {
            CreateConnectingOrderDTO.SegmentDTO segment = dto.getSegments().get(i);
            if (!flights.get(i).getId().equals(segment.getFlightId())) throw new BusinessException(ErrorCode.ITINERARY_INVALID);
            Set<Long> seatIds = new HashSet<>();
            List<FlightSeat> seats = new ArrayList<>();
            for (CreateConnectingOrderDTO.ItemDTO item : segment.getItems()) {
                if (!seatIds.add(item.getSeatId())) throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_ORDER);
                FlightSeat seat = flightMapper.findSeatById(item.getSeatId());
                if (seat == null || !segment.getFlightId().equals(seat.getFlightId()) || !"AVAILABLE".equals(seat.getStatus()))
                    throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
                seats.add(seat);
                ticketAmount = ticketAmount.add(seat.getPrice());
            }
            seatsBySegment.add(seats);
        }

        int passengerCount = expectedPassengers.size();
        TicketOrder order = new TicketOrder();
        order.setOrderNo("ORD" + LocalDateTime.now(businessClock).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + String.format("%04d", new Random().nextInt(10000)));
        order.setUserId(userId); order.setFlightId(flights.getFirst().getId()); order.setJourneyType("CONNECTING");
        order.setClientRequestId(requestId); order.setStatus(TicketOrder.STATUS_PENDING_PAYMENT);
        order.setTicketAmount(ticketAmount);
        order.setAirportFee(AIRPORT_FEE.multiply(BigDecimal.valueOf((long) passengerCount * 2)));
        order.setFuelFee(FUEL_FEE.multiply(BigDecimal.valueOf((long) passengerCount * 2)));
        order.setServiceFee(BigDecimal.ZERO);
        order.setTotalAmount(ticketAmount.add(order.getAirportFee()).add(order.getFuelFee()));
        LocalDateTime expiry = LocalDateTime.now(businessClock).plusMinutes(15);
        order.setExpireTime(expiry); orderMapper.insertOrder(order);

        List<Integer> lockOrder = java.util.stream.IntStream.range(0, 2).boxed()
                .sorted(Comparator.comparing(i -> flights.get(i).getId())).toList();
        for (int i : lockOrder) {
            FlightVO f = flights.get(i); List<FlightSeat> seats = seatsBySegment.get(i);
            int locked = flightMapper.lockSeats(seats.stream().map(FlightSeat::getId).sorted().toList(), order.getId(), expiry);
            if (locked != passengerCount || flightMapper.decrementRemainingSeats(f.getId(), passengerCount) != 1)
                throw new BusinessException(ErrorCode.SEAT_LOCK_FAILED);
        }

        for (int i = 0; i < 2; i++) {
            FlightVO f = flights.get(i);
            CreateConnectingOrderDTO.SegmentDTO requestSegment = dto.getSegments().get(i);
            List<FlightSeat> seats = seatsBySegment.get(i);
            TicketOrderSegment segment = snapshot(order.getId(), i + 1, f, seats);
            orderMapper.insertOrderSegment(segment);
            List<OrderSegmentPassenger> snapshots = new ArrayList<>();
            for (int j = 0; j < requestSegment.getItems().size(); j++) {
                var item = requestSegment.getItems().get(j); Passenger p = passengers.get(item.getPassengerId()); FlightSeat seat = seats.get(j);
                OrderSegmentPassenger sp = new OrderSegmentPassenger(); sp.setOrderSegmentId(segment.getId()); sp.setPassengerId(p.getId());
                sp.setPassengerName(p.getName()); sp.setPassengerType(p.getPassengerType()); sp.setSeatId(seat.getId()); sp.setSeatNo(seat.getSeatNo()); sp.setTicketPrice(seat.getPrice());
                snapshots.add(sp);
            }
            orderMapper.batchInsertSegmentPassengers(snapshots);
            if (i == 0) {
                List<OrderPassenger> legacy = snapshots.stream().map(sp -> { OrderPassenger p = new OrderPassenger(); p.setOrderId(order.getId()); p.setPassengerId(sp.getPassengerId()); p.setPassengerName(sp.getPassengerName()); p.setPassengerType(sp.getPassengerType()); p.setSeatId(sp.getSeatId()); p.setSeatNo(sp.getSeatNo()); p.setTicketPrice(sp.getTicketPrice()); return p; }).toList();
                orderMapper.batchInsertOrderPassengers(legacy);
            }
        }
        return orderService.getOrderDetailForUser(order.getId(), userId);
    }

    private Set<Long> passengerSet(CreateConnectingOrderDTO.SegmentDTO segment) {
        Set<Long> result = new HashSet<>();
        for (var item : segment.getItems()) if (!result.add(item.getPassengerId())) throw new BusinessException(ErrorCode.DUPLICATE_PASSENGER_IN_ORDER);
        return result;
    }

    private boolean matches(Long orderId, CreateConnectingOrderDTO dto) {
        var stored = orderMapper.findSegmentsByOrderId(orderId);
        if (stored.size() != dto.getSegments().size()) return false;
        for (int i = 0; i < stored.size(); i++) {
            if (!stored.get(i).getFlightId().equals(dto.getSegments().get(i).getFlightId())) return false;
            var expected = dto.getSegments().get(i).getItems().stream()
                    .collect(java.util.stream.Collectors.toMap(CreateConnectingOrderDTO.ItemDTO::getPassengerId,
                            CreateConnectingOrderDTO.ItemDTO::getSeatId, (a, b) -> a));
            var actual = orderMapper.findSegmentPassengers(stored.get(i).getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(OrderSegmentPassenger::getPassengerId,
                            OrderSegmentPassenger::getSeatId));
            if (!expected.equals(actual)) return false;
        }
        return true;
    }

    private TicketOrderSegment snapshot(Long orderId, int no, FlightVO f, List<FlightSeat> seats) {
        TicketOrderSegment s = new TicketOrderSegment(); s.setOrderId(orderId); s.setSegmentNo(no); s.setFlightId(f.getId()); s.setFlightNo(f.getFlightNo());
        s.setAirlineCode(f.getAirlineCode()); s.setAirlineName(f.getAirlineName()); s.setDepartureAirportCode(f.getDepartureAirportCode());
        s.setDepartureAirportName(f.getDepartureAirportName()); s.setDepartureCity(f.getDepartureCity()); s.setArrivalAirportCode(f.getArrivalAirportCode());
        s.setArrivalAirportName(f.getArrivalAirportName()); s.setArrivalCity(f.getArrivalCity()); s.setDepartureTime(f.getDepartureTime()); s.setArrivalTime(f.getArrivalTime());
        s.setTicketAmount(seats.stream().map(FlightSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)); return s;
    }
}
