package com.skybooker.change.service;

import com.skybooker.change.dto.ConnectingChangeDTO;
import com.skybooker.change.entity.ConnectingChangeRecord;
import com.skybooker.change.entity.ConnectingChangeSegmentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.change.mapper.ConnectingChangeMapper;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.dto.FlightSearchDTO;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.service.ItineraryService;
import com.skybooker.itinerary.vo.ItineraryVO;
import com.skybooker.order.entity.OrderSegmentPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.entity.TicketOrderSegment;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.service.OrderService;
import com.skybooker.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConnectingChangeService {
    private final OrderMapper orderMapper; private final FlightMapper flightMapper; private final ItineraryService itineraryService;
    private final ConnectingChangeMapper changeMapper; private final OrderService orderService; private final Clock businessClock;
    private final ObjectMapper objectMapper;

    public List<ItineraryVO> options(Long orderId) {
        return optionsCore(orderId, SecurityUtil.getCurrentUserId(), null, null);
    }
    public List<ItineraryVO> options(Long orderId, LocalDate startDate, LocalDate endDate) {
        return optionsCore(orderId, SecurityUtil.getCurrentUserId(), startDate, endDate);
    }
    public List<ItineraryVO> optionsForAdmin(Long orderId, LocalDate startDate, LocalDate endDate) {
        return optionsCore(orderId, null, startDate, endDate);
    }
    private List<ItineraryVO> optionsCore(Long orderId, Long userId, LocalDate requestedStart, LocalDate requestedEnd) {
        TicketOrder order = owned(orderId, userId);
        var current = orderMapper.findSegmentsByOrderId(orderId); requireItineraryManaged(current);
        ensureChangeWindow(current.getFirst().getDepartureTime(), false);
        LocalDate originalDate = current.getFirst().getDepartureTime().toLocalDate();
        LocalDate startDate = requestedStart == null ? originalDate : requestedStart;
        LocalDate endDate = requestedEnd == null ? startDate.plusDays(30) : requestedEnd;
        if (startDate.isBefore(originalDate) || endDate.isBefore(startDate) || endDate.isAfter(startDate.plusDays(30))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        FlightSearchDTO q = new FlightSearchDTO(); q.setDepartureCity(current.getFirst().getDepartureCity()); q.setArrivalCity(current.getLast().getArrivalCity());
        q.setPassengerCount(orderMapper.findSegmentPassengers(current.getFirst().getId()).size()); q.setSize(100);
        List<ItineraryVO> candidates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            q.setDepartureDate(date);
            candidates.addAll(itineraryService.search(q).getRecords());
        }
        return candidates.stream().filter(i -> i.getSegments().getFirst().getDepartureTime()
                .isAfter(current.getFirst().getDepartureTime().plusHours(2))).toList();
    }

    @Transactional
    public OrderVO change(Long orderId, ConnectingChangeDTO dto) {
        dto.setForce(false);
        return changeCore(orderId, SecurityUtil.getCurrentUserId(), dto);
    }
    @Transactional
    public OrderVO changeForAdmin(Long orderId, ConnectingChangeDTO dto) { return changeCore(orderId, null, dto); }
    private OrderVO changeCore(Long orderId, Long userId, ConnectingChangeDTO dto) {
        TicketOrder order = orderMapper.findByIdForUpdate(orderId);
        if (order == null || (userId != null && !userId.equals(order.getUserId()))) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        var oldSegments = orderMapper.findSegmentsByOrderId(orderId);
        requireItineraryManaged(oldSegments);
        ConnectingChangeRecord duplicate = changeMapper.findByUserAndRequest(order.getUserId(), dto.getClientRequestId().toString());
        if (duplicate != null) {
            if (!orderId.equals(duplicate.getOrderId()) || !matchesNewSnapshot(duplicate.getId(), dto))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return orderService.getOrderDetailForUser(orderId, order.getUserId());
        }
        if (!(TicketOrder.STATUS_ISSUED.equals(order.getStatus()) || TicketOrder.STATUS_CHANGED.equals(order.getStatus()))) throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        ensureChangeWindow(oldSegments.getFirst().getDepartureTime(), Boolean.TRUE.equals(dto.getForce()));
        Set<Long> passengers = orderMapper.findSegmentPassengers(oldSegments.getFirst().getId()).stream().map(OrderSegmentPassenger::getPassengerId).collect(java.util.stream.Collectors.toSet());
        for (var segment : dto.getSegments()) if (!passengers.equals(segment.getItems().stream().map(i -> i.getPassengerId()).collect(java.util.stream.Collectors.toSet()))) throw new BusinessException(ErrorCode.INCOMPLETE_SEGMENT_SEATS);
        List<FlightVO> flights = dto.getSegments().stream().map(s -> flightMapper.findPublishedFlightById(s.getFlightId())).toList(); itineraryService.validate(flights, passengers.size());
        if (!oldSegments.getFirst().getDepartureCity().equals(flights.getFirst().getDepartureCity()) || !oldSegments.getLast().getArrivalCity().equals(flights.getLast().getArrivalCity())) throw new BusinessException(ErrorCode.ITINERARY_INVALID);
        if (!flights.getFirst().getDepartureTime().isAfter(oldSegments.getFirst().getDepartureTime().plusHours(2))) throw new BusinessException(ErrorCode.CHANGE_FLIGHT_EARLIER_THAN_ORIGINAL);

        List<List<FlightSeat>> newSeats = new ArrayList<>(); BigDecimal ticket = BigDecimal.ZERO;
        for (int i=0;i<dto.getSegments().size();i++) { Set<Long> unique = new HashSet<>(); List<FlightSeat> list = new ArrayList<>();
            for (var item:dto.getSegments().get(i).getItems()) { if(!unique.add(item.getSeatId())) throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_ORDER); FlightSeat s=flightMapper.findSeatById(item.getSeatId()); if(s==null||!flights.get(i).getId().equals(s.getFlightId())||!"AVAILABLE".equals(s.getStatus())) throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE); list.add(s); ticket=ticket.add(s.getPrice()); } newSeats.add(list); }
        BigDecimal airport=new BigDecimal("50.00").multiply(BigDecimal.valueOf((long)passengers.size()*flights.size()));
        BigDecimal fuel=new BigDecimal("30.00").multiply(BigDecimal.valueOf((long)passengers.size()*flights.size()));
        BigDecimal total=ticket.add(airport).add(fuel);
        BigDecimal feeRate = Duration.between(LocalDateTime.now(businessClock), oldSegments.getFirst().getDepartureTime())
                .compareTo(Duration.ofHours(24)) > 0 ? new BigDecimal("0.10") : new BigDecimal("0.30");
        BigDecimal fee=order.getTotalAmount().multiply(feeRate).setScale(2,RoundingMode.HALF_UP);
        ConnectingChangeRecord rec=new ConnectingChangeRecord();rec.setOrderId(orderId);rec.setUserId(order.getUserId());rec.setClientRequestId(dto.getClientRequestId().toString());rec.setOldTotalAmount(order.getTotalAmount());rec.setNewTotalAmount(total);rec.setPriceDifference(total.subtract(order.getTotalAmount()));rec.setChangeFee(fee);rec.setReason(dto.getReason());rec.setStatus("SUCCESS");changeMapper.insert(rec);
        changeMapper.insertSnapshotsFromOrder(rec.getId(), "OLD", orderId);
        LocalDateTime lockUntil=LocalDateTime.now(businessClock).plusMinutes(5);
        var lockOrder=java.util.stream.IntStream.range(0,flights.size()).boxed().sorted(Comparator.comparing(i->flights.get(i).getId())).toList();
        for(int i:lockOrder){ var ids=newSeats.get(i).stream().map(FlightSeat::getId).sorted().toList(); if(flightMapper.lockSeats(ids,orderId,lockUntil)!=passengers.size()||flightMapper.decrementRemainingSeats(flights.get(i).getId(),passengers.size())!=1) throw new BusinessException(ErrorCode.SEAT_LOCK_FAILED); }
        List<Long> oldSeatIds=oldSegments.stream().flatMap(s->orderMapper.findSegmentPassengers(s.getId()).stream()).map(OrderSegmentPassenger::getSeatId).toList();
        if(flightMapper.releaseSoldSeatsBySeatIds(oldSeatIds)!=oldSeatIds.size()) throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        oldSegments.forEach(s->flightMapper.incrementRemainingSeats(s.getFlightId(),orderMapper.findSegmentPassengers(s.getId()).size()));
        orderMapper.deleteSegmentPassengersByOrderId(orderId); orderMapper.deleteSegmentsByOrderId(orderId);
        for(int i=0;i<flights.size();i++){ TicketOrderSegment seg=snapshot(orderId,i+1,flights.get(i),newSeats.get(i)); orderMapper.insertOrderSegment(seg); List<OrderSegmentPassenger> ps=new ArrayList<>(); for(int j=0;j<dto.getSegments().get(i).getItems().size();j++){var item=dto.getSegments().get(i).getItems().get(j); var old=orderMapper.findPassengersByOrderId(orderId).stream().filter(p->p.getPassengerId().equals(item.getPassengerId())).findFirst().orElseThrow(); var seat=newSeats.get(i).get(j); OrderSegmentPassenger p=new OrderSegmentPassenger();p.setOrderSegmentId(seg.getId());p.setPassengerId(old.getPassengerId());p.setPassengerName(old.getPassengerName());p.setPassengerType(old.getPassengerType());p.setSeatId(seat.getId());p.setSeatNo(seat.getSeatNo());p.setTicketPrice(seat.getPrice());ps.add(p);}orderMapper.batchInsertSegmentPassengers(ps); if(i==0)for(var p:ps){var legacy=orderMapper.findPassengersByOrderId(orderId).stream().filter(x->x.getPassengerId().equals(p.getPassengerId())).findFirst().orElseThrow();orderMapper.updateOrderPassengerSeat(legacy.getId(),p.getSeatId(),p.getSeatNo(),p.getTicketPrice());}}
        changeMapper.insertSnapshotsFromOrder(rec.getId(), "NEW", orderId);
        int sold=flightMapper.updateSeatStatusToSold(orderId); if(sold!=newSeats.stream().mapToInt(List::size).sum()) throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        orderMapper.updateOrderFlightAndAmounts(orderId,flights.getFirst().getId(),
                flights.size() == 1 ? "DIRECT" : "CONNECTING",ticket,airport,fuel,BigDecimal.ZERO,total); if(orderMapper.updateOrderStatusCAS(orderId,order.getStatus(),TicketOrder.STATUS_CHANGED)!=1) throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        return orderService.getOrderDetailForUser(orderId,order.getUserId());
    }
    private TicketOrder owned(Long id,Long user){TicketOrder o=orderMapper.findById(id);if(o==null||(user!=null&&!user.equals(o.getUserId())))throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return o;}
    private void requireItineraryManaged(List<TicketOrderSegment> segments){if(segments.isEmpty())throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);}
    private boolean matchesNewSnapshot(Long changeRecordId, ConnectingChangeDTO dto) {
        List<ConnectingChangeSegmentSnapshot> stored = changeMapper.findSegmentSnapshots(changeRecordId, "NEW");
        if (stored.size() != dto.getSegments().size()) return false;
        try {
            for (int i = 0; i < stored.size(); i++) {
                var requested = dto.getSegments().get(i);
                if (!Objects.equals(stored.get(i).getFlightId(), requested.getFlightId())) return false;
                Map<Long, Long> expected = new HashMap<>();
                for (var item : requested.getItems()) {
                    if (expected.put(item.getPassengerId(), item.getSeatId()) != null) return false;
                }
                Map<Long, Long> actual = new HashMap<>();
                JsonNode seats = objectMapper.readTree(stored.get(i).getPassengerSeats());
                for (JsonNode seat : seats) {
                    if (actual.put(seat.get("passengerId").asLong(), seat.get("seatId").asLong()) != null) return false;
                }
                if (!expected.equals(actual)) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
    private void ensureChangeWindow(LocalDateTime departure,boolean force){if(!force&&Duration.between(LocalDateTime.now(businessClock),departure).compareTo(Duration.ofHours(2))<0)throw new BusinessException(ErrorCode.CHANGE_WINDOW_CLOSED);}
    private TicketOrderSegment snapshot(Long oid,int no,FlightVO f,List<FlightSeat> seats){TicketOrderSegment s=new TicketOrderSegment();s.setOrderId(oid);s.setSegmentNo(no);s.setFlightId(f.getId());s.setFlightNo(f.getFlightNo());s.setAirlineCode(f.getAirlineCode());s.setAirlineName(f.getAirlineName());s.setDepartureAirportCode(f.getDepartureAirportCode());s.setDepartureAirportName(f.getDepartureAirportName());s.setDepartureCity(f.getDepartureCity());s.setArrivalAirportCode(f.getArrivalAirportCode());s.setArrivalAirportName(f.getArrivalAirportName());s.setArrivalCity(f.getArrivalCity());s.setDepartureTime(f.getDepartureTime());s.setArrivalTime(f.getArrivalTime());s.setTicketAmount(seats.stream().map(FlightSeat::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add));return s;}
}
