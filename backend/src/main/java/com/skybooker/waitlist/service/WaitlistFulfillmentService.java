package com.skybooker.waitlist.service;

import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.order.entity.OrderPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.waitlist.mapper.WaitlistMapper;
import com.skybooker.waitlist.vo.WaitlistVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistFulfillmentService {

    private final WaitlistMapper waitlistMapper;
    private final FlightMapper flightMapper;
    private final OrderMapper orderMapper;

    @Transactional
    public int tryFulfillWaitlists(Long flightId, String cabinClass, int releasedCount, Long flightIdForSeats) {
        if (releasedCount <= 0) {
            return 0;
        }

        List<WaitlistVO> waiting = waitlistMapper.findWaitingByFlightAndCabin(flightId, cabinClass);
        if (waiting.isEmpty()) {
            return 0;
        }

        int consumedByFulfillment = 0;

        for (WaitlistVO wl : waiting) {
            WaitlistVO detail = waitlistMapper.findDetailById(wl.getId());
            if (detail == null || detail.getPassengerCount() == null || detail.getPassengers() == null || detail.getPassengers().isEmpty()) {
                continue;
            }

            int needed = detail.getPassengerCount();
            int currentAvailable = flightMapper.countAvailableSeatsByFlightAndCabin(flightId, cabinClass);

            if (currentAvailable < needed) {
                waitlistMapper.updateSkipReason(wl.getId(), "可用座位不足: 需要" + needed + "，当前" + currentAvailable);
                continue;
            }

            List<FlightSeat> seats = flightMapper.findAvailableSeatsByFlightAndCabin(
                    flightId, cabinClass, needed);
            if (seats.size() < needed) {
                waitlistMapper.updateSkipReason(wl.getId(), "座位不足");
                continue;
            }

            List<Long> seatIds = seats.stream().map(FlightSeat::getId).toList();
            TicketOrder ticketOrder = new TicketOrder();
            ticketOrder.setOrderNo(generateOrderNo());
            ticketOrder.setUserId(detail.getUserId());
            ticketOrder.setFlightId(flightId);
            ticketOrder.setStatus("ISSUED");

            BigDecimal payAmount = detail.getPayAmount();
            int pCount = detail.getPassengerCount();
            BigDecimal airportFee = new BigDecimal("50.00").multiply(BigDecimal.valueOf(pCount));
            BigDecimal fuelFee = new BigDecimal("30.00").multiply(BigDecimal.valueOf(pCount));
            BigDecimal serviceFee = BigDecimal.ZERO;
            BigDecimal ticketAmount = payAmount.subtract(airportFee).subtract(fuelFee).subtract(serviceFee);

            ticketOrder.setTicketAmount(ticketAmount);
            ticketOrder.setAirportFee(airportFee);
            ticketOrder.setFuelFee(fuelFee);
            ticketOrder.setServiceFee(serviceFee);
            ticketOrder.setTotalAmount(payAmount);
            ticketOrder.setPayTime(detail.getPaidAt());
            orderMapper.insertOrder(ticketOrder);

            int locked = flightMapper.lockAvailableSeatsForWaitlist(seatIds, ticketOrder.getId());
            if (locked != needed) {
                log.warn("Waitlist fulfillment seat lock mismatch for waitlist {}: needed={}, locked={}",
                        wl.getId(), needed, locked);
                throw new RuntimeException("Seat lock mismatch during waitlist fulfillment");
            }

            List<OrderPassenger> orderPassengers = new ArrayList<>();
            List<WaitlistVO.WaitlistPassengerVO> wlPassengers = detail.getPassengers();
            for (int i = 0; i < wlPassengers.size(); i++) {
                WaitlistVO.WaitlistPassengerVO wp = wlPassengers.get(i);
                OrderPassenger op = new OrderPassenger();
                op.setOrderId(ticketOrder.getId());
                op.setPassengerId(wp.getPassengerId());
                op.setPassengerName(wp.getPassengerName());
                op.setPassengerType(wp.getPassengerType());
                op.setSeatId(seats.get(i).getId());
                op.setSeatNo(seats.get(i).getSeatNo());
                op.setTicketPrice(seats.get(i).getPrice());
                orderPassengers.add(op);
            }
            orderMapper.batchInsertOrderPassengers(orderPassengers);

            waitlistMapper.updateTicketOrderId(wl.getId(), ticketOrder.getId());
            int cas = waitlistMapper.updateStatusCAS(wl.getId(), "WAITING", "SUCCESS");
            if (cas == 0) {
                throw new RuntimeException("Waitlist " + wl.getId() + " state changed during fulfillment, expected WAITING");
            }

            consumedByFulfillment += needed;
        }

        if (consumedByFulfillment > 0) {
            flightMapper.decrementRemainingSeats(flightId, consumedByFulfillment);
        }

        return consumedByFulfillment;
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
    }
}
