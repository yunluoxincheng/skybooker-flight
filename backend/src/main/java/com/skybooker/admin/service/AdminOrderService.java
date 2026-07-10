package com.skybooker.admin.service;

import com.skybooker.admin.dto.AdminCancelOrderDTO;
import com.skybooker.admin.dto.AdminChangeDTO;
import com.skybooker.admin.dto.AdminCreateOrderDTO;
import com.skybooker.admin.dto.AdminNoteDTO;
import com.skybooker.admin.dto.AdminRefundDTO;
import com.skybooker.admin.dto.AdminVoidDTO;
import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.admin.vo.AdminOrderDetailVO;
import com.skybooker.admin.vo.AdminOrderTimelineItemVO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.change.entity.ChangeRecord;
import com.skybooker.change.mapper.ChangeMapper;
import com.skybooker.change.mapper.ConnectingChangeMapper;
import com.skybooker.change.service.ChangeService;
import com.skybooker.change.vo.ChangeOptionVO;
import com.skybooker.change.vo.ChangeOrderResultVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightSeatVO;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.service.OrderService;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.passenger.mapper.PassengerMapper;
import com.skybooker.passenger.vo.PassengerVO;
import com.skybooker.refund.entity.RefundRecord;
import com.skybooker.refund.mapper.RefundMapper;
import com.skybooker.refund.service.RefundService;
import com.skybooker.refund.vo.RefundVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final AdminMapper adminMapper;
    private final AuthMapper authMapper;
    private final AdminOperationLogService operationLogService;
    private final OrderService orderService;
    private final RefundService refundService;
    private final ChangeService changeService;
    private final OrderMapper orderMapper;
    private final RefundMapper refundMapper;
    private final ChangeMapper changeMapper;
    private final ConnectingChangeMapper connectingChangeMapper;
    private final FlightMapper flightMapper;
    private final PassengerMapper passengerMapper;

    @Transactional
    public OrderVO createOrderForUser(AdminCreateOrderDTO dto) {
        Long adminUserId = currentAdminId();
        Long targetUserId = resolveTargetUserId(dto);
        requireNormalOrdinaryUser(targetUserId);
        OrderVO order = orderService.createOrderCore(targetUserId, dto.toCreateOrderDTO());
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_ORDER, order.getId(),
                AdminOperationLogService.ACTION_ORDER_CREATE, null);
        return order;
    }

    @Transactional
    public RefundVO refund(Long orderId, AdminRefundDTO dto) {
        Long adminUserId = currentAdminId();
        TicketOrder order = findOrder(orderId);
        boolean force = Boolean.TRUE.equals(dto.getForce());
        RefundVO refund = refundService.refundOrderCore(orderId, order.getUserId(), dto.getReason(), force);
        if (TicketOrder.STATUS_REFUNDED.equals(order.getStatus())) {
            return refund;
        }
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_ORDER, orderId,
                force ? AdminOperationLogService.ACTION_REFUND_FORCE : AdminOperationLogService.ACTION_REFUND,
                dto.getReason());
        return refund;
    }

    @Transactional
    public OrderVO cancel(Long orderId, AdminCancelOrderDTO dto) {
        Long adminUserId = currentAdminId();
        TicketOrder order = findOrder(orderId);
        if (!TicketOrder.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }

        OrderService.CancelOrderResult result = orderService.cancelOrderCore(orderId, order.getUserId());
        if (!result.transitioned()) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_ORDER, orderId,
                AdminOperationLogService.ACTION_ORDER_CANCEL, dto.getReason());
        return result.order();
    }

    @Transactional
    public ChangeOrderResultVO change(Long orderId, AdminChangeDTO dto) {
        Long adminUserId = currentAdminId();
        TicketOrder order = findOrder(orderId);
        boolean force = Boolean.TRUE.equals(dto.getForce());
        ChangeOrderResultVO result = changeService.changeOrderCore(orderId, order.getUserId(), dto.toChangeOrderDTO(), force);
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_ORDER, orderId,
                force ? AdminOperationLogService.ACTION_CHANGE_FORCE : AdminOperationLogService.ACTION_CHANGE,
                dto.getReason());
        return result;
    }

    @Transactional
    public OrderVO voidOrder(Long orderId, AdminVoidDTO dto) {
        return voidOrderWithReason(orderId, dto.getReason());
    }

    @Transactional
    public OrderVO deleteOrderAsVoid(Long orderId, String type, String reason) {
        if (type == null || type.isBlank() || !"delete".equals(type)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return voidOrderWithReason(orderId, reason);
    }

    @Transactional
    public OrderVO voidOrderWithReason(Long orderId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Long adminUserId = currentAdminId();
        TicketOrder order = findOrder(orderId);
        if (!TicketOrder.STATUS_CANCELLED.equals(order.getStatus())
                && !TicketOrder.STATUS_REFUNDED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_VOIDABLE);
        }

        int cas = orderMapper.updateOrderStatusCAS(orderId, order.getStatus(), TicketOrder.STATUS_VOIDED);
        if (cas == 0) {
            throw new BusinessException(ErrorCode.ORDER_NOT_VOIDABLE);
        }
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_ORDER, orderId,
                AdminOperationLogService.ACTION_VOID, reason);
        return orderMapper.findDetailById(orderId);
    }

    @Transactional
    public OrderVO updateAdminNote(Long orderId, AdminNoteDTO dto) {
        Long adminUserId = currentAdminId();
        findOrder(orderId);
        int updated = orderMapper.updateAdminNote(orderId, dto.getAdminNote());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_ORDER, orderId,
                AdminOperationLogService.ACTION_ADMIN_NOTE_UPDATE, null);
        return orderMapper.findDetailById(orderId);
    }

    public List<RefundRecord> listRefundRecords(Long orderId) {
        findOrder(orderId);
        return refundMapper.findRecordsByOrderId(orderId);
    }

    public List<ChangeRecord> listChangeRecords(Long orderId) {
        findOrder(orderId);
        return changeMapper.findByOrderId(orderId);
    }

    public AdminOrderDetailVO getEnhancedOrderDetail(Long orderId) {
        TicketOrder entity = findOrder(orderId);
        OrderVO order = orderService.getOrderDetailForUser(orderId, entity.getUserId());
        List<RefundRecord> refunds = refundMapper.findRecordsByOrderId(orderId);
        List<ChangeRecord> changes = changeMapper.findByOrderId(orderId);
        return AdminOrderDetailVO.from(order, refunds, changes, connectingChangeMapper.findByOrderId(orderId), buildTimeline(order, entity, refunds, changes));
    }

    public List<ChangeOptionVO> listChangeOptions(Long orderId) {
        findOrder(orderId);
        return changeService.listChangeOptionsForAdmin(orderId);
    }

    public List<PassengerVO> listPassengersByUser(Long userId) {
        requireOrdinaryUser(userId);
        return passengerMapper.findByUserId(userId);
    }

    public List<FlightSeatVO> listFlightSeats(Long flightId) {
        Flight flight = flightMapper.findById(flightId);
        if (flight == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return flightMapper.findSeatsByFlightId(flightId);
    }

    private TicketOrder findOrder(Long orderId) {
        TicketOrder order = orderMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private void requireNormalOrdinaryUser(Long userId) {
        User user = authMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"NORMAL".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private void requireOrdinaryUser(Long userId) {
        User user = authMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Long resolveTargetUserId(AdminCreateOrderDTO dto) {
        if (dto.hasConflictingUserAlias()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Long targetUserId = dto.resolveTargetUserId();
        if (targetUserId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return targetUserId;
    }

    private List<AdminOrderTimelineItemVO> buildTimeline(OrderVO order,
                                                         TicketOrder entity,
                                                         List<RefundRecord> refunds,
                                                         List<ChangeRecord> changes) {
        List<AdminOrderTimelineItemVO> timeline = new ArrayList<>();
        addTimeline(timeline, "CREATED", TicketOrder.STATUS_PENDING_PAYMENT, "Order created", order.getCreatedAt());
        addTimeline(timeline, "PAID", TicketOrder.STATUS_ISSUED, "Order paid and issued", order.getPayTime());
        for (ChangeRecord change : changes) {
            addTimeline(timeline, "CHANGED", TicketOrder.STATUS_CHANGED, "Order changed", change.getCreatedAt());
        }
        for (RefundRecord refund : refunds) {
            addTimeline(timeline, "REFUNDED", TicketOrder.STATUS_REFUNDED, "Order refunded", refund.getCreatedAt());
        }
        if (TicketOrder.STATUS_VOIDED.equals(order.getStatus())) {
            LocalDateTime voidedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt() : order.getCreatedAt();
            addTimeline(timeline, "VOIDED", TicketOrder.STATUS_VOIDED, "Order voided", voidedAt);
        }
        timeline.sort(Comparator.comparing(AdminOrderTimelineItemVO::getOccurredAt));
        return timeline;
    }

    private void addTimeline(List<AdminOrderTimelineItemVO> timeline,
                             String eventType,
                             String status,
                             String description,
                             LocalDateTime occurredAt) {
        if (occurredAt != null) {
            timeline.add(new AdminOrderTimelineItemVO(eventType, status, description, occurredAt));
        }
    }

    private Long currentAdminId() {
        AdminUser adminUser = adminMapper.findByUserId(SecurityUtil.getCurrentUserId());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return adminUser.getId();
    }
}
