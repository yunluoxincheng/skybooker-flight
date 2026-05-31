package com.skybooker.refund.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.refund.entity.RefundRecord;
import com.skybooker.refund.mapper.RefundMapper;
import com.skybooker.refund.vo.RefundVO;
import com.skybooker.waitlist.service.WaitlistFulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;
    private final RefundMapper refundMapper;
    private final WaitlistFulfillmentService waitlistFulfillmentService;

    @Transactional
    public RefundVO refundOrder(Long orderId) {
        Long userId = SecurityUtil.getCurrentUserId();
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if ("REFUNDED".equals(order.getStatus())) {
            return refundMapper.findByOrderId(orderId);
        }

        if (!"ISSUED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        Flight flight = flightMapper.findById(order.getFlightId());
        validateRefundWindow(flight);

        BigDecimal feeRate = calculateFeeRate(flight);
        BigDecimal feeAmount = order.getTotalAmount().multiply(feeRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = order.getTotalAmount().subtract(feeAmount)
                .setScale(2, RoundingMode.HALF_UP);

        int cas = orderMapper.updateOrderStatusCAS(orderId, "ISSUED", "REFUNDED");
        if (cas == 0) {
            return refundMapper.findByOrderId(orderId);
        }

        RefundRecord record = new RefundRecord();
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setReason("用户主动退款");
        record.setRefundAmount(refundAmount);
        record.setFeeAmount(feeAmount);
        record.setStatus("SUCCESS");
        refundMapper.insertRefundRecord(record);

        int passengerCount = countOrderPassengers(orderId);
        List<String> cabinClasses = flightMapper.findCabinClassesByOrderId(orderId);

        flightMapper.releaseSoldSeatsByOrderId(orderId);
        flightMapper.incrementRemainingSeats(order.getFlightId(), passengerCount);

        for (String cabinClass : cabinClasses) {
            waitlistFulfillmentService.tryFulfillWaitlists(
                    order.getFlightId(), cabinClass, passengerCount);
        }

        return refundMapper.findByOrderId(orderId);
    }

    private void validateRefundWindow(Flight flight) {
        Duration remaining = Duration.between(LocalDateTime.now(), flight.getDepartureTime());
        if (remaining.compareTo(Duration.ofHours(2)) < 0) {
            throw new BusinessException(ErrorCode.REFUND_WINDOW_CLOSED);
        }
    }

    private BigDecimal calculateFeeRate(Flight flight) {
        Duration remaining = Duration.between(LocalDateTime.now(), flight.getDepartureTime());
        if (remaining.compareTo(Duration.ofHours(24)) > 0) {
            return new BigDecimal("0.10");
        }
        return new BigDecimal("0.30");
    }

    private int countOrderPassengers(Long orderId) {
        com.skybooker.order.vo.OrderVO detail = orderMapper.findDetailById(orderId);
        return detail != null && detail.getPassengers() != null ? detail.getPassengers().size() : 0;
    }
}
