package com.skybooker.order.service;

import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCleanupService {

    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;

    @Transactional
    public void cleanupExpiredOrder(Long orderId) {
        TicketOrder order = orderMapper.findById(orderId);
        if (order != null && "PENDING_PAYMENT".equals(order.getStatus())
                && order.getExpireTime() != null && order.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
            int passengerCount = countOrderPassengers(orderId);
            orderMapper.updateOrderStatus(orderId, "CANCELLED");
            flightMapper.releaseSeatsByOrderId(orderId);
            flightMapper.incrementRemainingSeats(order.getFlightId(), passengerCount);
        }
    }

    @Transactional
    public void cleanupAllExpiredOrders() {
        List<TicketOrder> expired = orderMapper.findExpiredPendingOrders();
        for (TicketOrder order : expired) {
            try {
                int passengerCount = countOrderPassengers(order.getId());
                orderMapper.updateOrderStatus(order.getId(), "CANCELLED");
                flightMapper.releaseSeatsByOrderId(order.getId());
                flightMapper.incrementRemainingSeats(order.getFlightId(), passengerCount);
            } catch (Exception e) {
                log.warn("Failed to cleanup expired order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    private int countOrderPassengers(Long orderId) {
        OrderVO detail = orderMapper.findDetailById(orderId);
        return detail != null && detail.getPassengers() != null ? detail.getPassengers().size() : 0;
    }
}
