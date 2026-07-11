package com.skybooker.refund.service;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.order.entity.OrderPassenger;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.refund.entity.RefundRecord;
import com.skybooker.refund.mapper.RefundMapper;
import com.skybooker.refund.vo.RefundVO;
import com.skybooker.waitlist.service.WaitlistFulfillmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final OrderMapper orderMapper;
    private final FlightMapper flightMapper;
    private final RefundMapper refundMapper;
    private final WaitlistFulfillmentService waitlistFulfillmentService;
    private final Clock businessClock;

    @Transactional
    public RefundVO refundOrder(Long orderId, String reason) {
        Long userId = SecurityUtil.getCurrentUserId();
        return refundOrderCore(orderId, userId, reason, false);
    }

    @Transactional
    public RefundVO refundOrderCore(Long orderId, Long userId, String reason, boolean force) {
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (TicketOrder.STATUS_REFUNDED.equals(order.getStatus())) {
            return refundMapper.findByOrderId(orderId);
        }

        if (!isRefundableStatus(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        Flight flight = flightMapper.findById(order.getFlightId());
        if (!force) {
            validateRefundWindow(flight);
        }

        BigDecimal feeRate = calculateFeeRate(flight);
        BigDecimal feeAmount = order.getTotalAmount().multiply(feeRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = order.getTotalAmount().subtract(feeAmount)
                .setScale(2, RoundingMode.HALF_UP);

        int cas = orderMapper.updateOrderStatusCAS(orderId, order.getStatus(), TicketOrder.STATUS_REFUNDED);
        if (cas == 0) {
            RefundVO existing = refundMapper.findByOrderId(orderId);
            if (existing != null) {
                return existing;
            }
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        RefundRecord record = new RefundRecord();
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setReason(reason);
        record.setRefundAmount(refundAmount);
        record.setFeeAmount(feeAmount);
        record.setStatus("SUCCESS");
        refundMapper.insertRefundRecord(record);

        // H3: 按 order_passenger.seat_id 快照释放座位,而非按 orderId 全量。
        // 改签后 order_passenger.seat_id 已更新为新座,此处只释放当前关联座位,
        // 避免 orderId 名下残留脏 SOLD(异常/并发历史数据)被误释放,导致
        // incrementRemainingSeats(当前航班) 与实际释放座位所属航班错配。
        var segments = orderMapper.findSegmentsByOrderId(orderId);
        if (!segments.isEmpty()) {
            var seatIds = segments.stream().flatMap(s -> orderMapper.findSegmentPassengers(s.getId()).stream())
                    .map(com.skybooker.order.entity.OrderSegmentPassenger::getSeatId).toList();
            List<WaitlistRelease> releases = new ArrayList<>();
            for (var segment : segments) {
                List<Long> segmentSeatIds = orderMapper.findSegmentPassengers(segment.getId()).stream()
                        .map(com.skybooker.order.entity.OrderSegmentPassenger::getSeatId).toList();
                for (String cabinClass : flightMapper.findCabinClassesBySeatIds(segmentSeatIds)) {
                    releases.add(new WaitlistRelease(segment.getFlightId(), cabinClass,
                            flightMapper.countSeatsByIdsAndCabin(segmentSeatIds, cabinClass)));
                }
            }
            int released = flightMapper.releaseSoldSeatsBySeatIds(seatIds);
            if (released != seatIds.size()) throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            segments.forEach(s -> flightMapper.incrementRemainingSeats(s.getFlightId(), orderMapper.findSegmentPassengers(s.getId()).size()));
            registerWaitlistFulfillment(orderId, releases);
            return refundMapper.findByOrderId(orderId);
        }

        List<OrderPassenger> passengers = orderMapper.findPassengersByOrderId(orderId);
        List<Long> refundSeatIds = passengers.stream().map(OrderPassenger::getSeatId).toList();
        // H3: cabinClasses 同样基于 refundSeatIds 快照,与"释放哪些座位""回补多少余票"范围一致,
        // 避免 orderId 名下脏 SOLD 的舱位错误进入候补兑现流程。
        List<String> cabinClasses = flightMapper.findCabinClassesBySeatIds(refundSeatIds);

        int released = flightMapper.releaseSoldSeatsBySeatIds(refundSeatIds);
        if (released != refundSeatIds.size()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        int passengerCount = passengers.size();
        flightMapper.incrementRemainingSeats(order.getFlightId(), passengerCount);

        final Long flightId = order.getFlightId();
        final int pCount = passengerCount;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String cabinClass : cabinClasses) {
                    try {
                        waitlistFulfillmentService.tryFulfillWaitlists(flightId, cabinClass, pCount);
                    } catch (Exception e) {
                        log.warn("候补兑现失败(退款已提交)order={}, cabin={}", orderId, cabinClass, e);
                    }
                }
            }
        });

        return refundMapper.findByOrderId(orderId);
    }

    private void registerWaitlistFulfillment(Long orderId, List<WaitlistRelease> releases) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (WaitlistRelease release : releases) {
                    try {
                        waitlistFulfillmentService.tryFulfillWaitlists(
                                release.flightId(), release.cabinClass(), release.count());
                    } catch (Exception e) {
                        log.warn("候补兑现失败(联程退款已提交)order={}, flight={}, cabin={}",
                                orderId, release.flightId(), release.cabinClass(), e);
                    }
                }
            }
        });
    }

    private record WaitlistRelease(Long flightId, String cabinClass, int count) {}

    private void validateRefundWindow(Flight flight) {
        Duration remaining = Duration.between(LocalDateTime.now(businessClock), flight.getDepartureTime());
        if (remaining.compareTo(Duration.ofHours(2)) < 0) {
            throw new BusinessException(ErrorCode.REFUND_WINDOW_CLOSED);
        }
    }

    private BigDecimal calculateFeeRate(Flight flight) {
        Duration remaining = Duration.between(LocalDateTime.now(businessClock), flight.getDepartureTime());
        if (remaining.compareTo(Duration.ofHours(24)) > 0) {
            return new BigDecimal("0.10");
        }
        return new BigDecimal("0.30");
    }

    private boolean isRefundableStatus(String status) {
        return TicketOrder.STATUS_ISSUED.equals(status) || TicketOrder.STATUS_CHANGED.equals(status);
    }
}
