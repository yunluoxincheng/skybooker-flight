package com.skybooker.admin.service;

import com.skybooker.admin.dto.AdminChangeDTO;
import com.skybooker.admin.dto.AdminCreateOrderDTO;
import com.skybooker.admin.dto.AdminNoteDTO;
import com.skybooker.admin.dto.AdminRefundDTO;
import com.skybooker.admin.dto.AdminVoidDTO;
import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.change.entity.ChangeRecord;
import com.skybooker.change.mapper.ChangeMapper;
import com.skybooker.change.service.ChangeService;
import com.skybooker.change.vo.ChangeOrderResultVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.order.entity.TicketOrder;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.service.OrderService;
import com.skybooker.order.vo.OrderVO;
import com.skybooker.refund.entity.RefundRecord;
import com.skybooker.refund.mapper.RefundMapper;
import com.skybooker.refund.service.RefundService;
import com.skybooker.refund.vo.RefundVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public OrderVO createOrderForUser(AdminCreateOrderDTO dto) {
        Long adminUserId = currentAdminId();
        requireNormalOrdinaryUser(dto.getTargetUserId());
        OrderVO order = orderService.createOrderCore(dto.getTargetUserId(), dto.toCreateOrderDTO());
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
                AdminOperationLogService.ACTION_VOID, dto.getReason());
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

    private Long currentAdminId() {
        AdminUser adminUser = adminMapper.findByUserId(SecurityUtil.getCurrentUserId());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return adminUser.getId();
    }
}
