package com.skybooker.order.service;

import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@Slf4j
public class OrderCleanupService {

    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;
    private final Clock businessClock;
    private OrderCleanupService self;

    public OrderCleanupService(OrderMapper orderMapper, FlightMapper flightMapper, Clock businessClock) {
        this.orderMapper = orderMapper;
        this.flightMapper = flightMapper;
        this.businessClock = businessClock;
    }

    @Autowired
    public void setSelf(@Lazy OrderCleanupService self) {
        this.self = self;
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupAllExpiredOrders() {
        List<TicketOrder> expired = orderMapper.findExpiredPendingOrders();
        for (TicketOrder order : expired) {
            try {
                self.cleanupExpiredOrder(order.getId());
            } catch (Exception e) {
                log.warn("Failed to cleanup expired order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void cleanupExpiredOrder(Long orderId) {
        TicketOrder order = orderMapper.findById(orderId);
        if (order != null && "PENDING_PAYMENT".equals(order.getStatus())
                && order.getExpireTime() != null && order.getExpireTime().isBefore(java.time.LocalDateTime.now(businessClock))) {
            cancelSingleExpiredOrder(order);
        }
    }

    private void cancelSingleExpiredOrder(TicketOrder order) {
        int affected = orderMapper.updateOrderStatusCAS(order.getId(), "PENDING_PAYMENT", "CANCELLED");
        if (affected == 0) {
            return;
        }
        int passengerCount = countOrderPassengers(order.getId());
        flightMapper.releaseSeatsByOrderId(order.getId());
        flightMapper.incrementRemainingSeats(order.getFlightId(), passengerCount);
    }

    private int countOrderPassengers(Long orderId) {
        OrderVO detail = orderMapper.findDetailById(orderId);
        return detail != null && detail.getPassengers() != null ? detail.getPassengers().size() : 0;
    }
}
