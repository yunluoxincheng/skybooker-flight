package com.skybooker.admin.service;

import com.skybooker.admin.dto.AdminCreateUserDTO;
import com.skybooker.admin.dto.AdminOrderQueryDTO;
import com.skybooker.admin.dto.PageQueryDTO;
import com.skybooker.admin.dto.AdminUserQueryDTO;
import com.skybooker.admin.support.AdminListQuerySupport;
import com.skybooker.admin.vo.UserAdminVO;
import com.skybooker.admin.vo.UserDeleteCheckVO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> VALID_ORDER_STATUSES = Set.of(
            "PENDING_PAYMENT", "ISSUED", "CANCELLED", "REFUNDED", "CHANGED", "CHANGE_PENDING", "VOIDED");
    private static final Set<String> VALID_USER_STATUSES = Set.of("NORMAL", "DISABLED", "DELETED");

    private final AuthMapper authMapper;
    private final OrderMapper orderMapper;
    private final AdminOperationLogService operationLogService;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<UserAdminVO> listUsers(AdminUserQueryDTO query) {
        AdminListQuerySupport.normalize(query);
        validateUserStatus(query.getStatus());
        int offset = AdminListQuerySupport.offset(query);
        List<UserAdminVO> users = authMapper.findUsersByRole("USER", query.getKeyword(), query.getEmail(),
                query.getNickname(), query.getStatus(), offset, query.getSize());
        long total = authMapper.countUsersByRole("USER", query.getKeyword(), query.getEmail(), query.getNickname(),
                query.getStatus());
        return new PageResponse<>(users, total, query.getPage(), query.getSize());
    }

    private void validateUserStatus(String status) {
        if (status != null && !VALID_USER_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Transactional
    public UserAdminVO createUser(AdminCreateUserDTO dto) {
        Long adminUserId = currentAdminId();
        User existing = authMapper.findByEmail(dto.getEmail());
        if (existing != null) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRealName(dto.getRealName());
        user.setRole("USER");
        user.setStatus("NORMAL");
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        authMapper.insertByAdmin(user);

        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_USER, user.getId(),
                AdminOperationLogService.ACTION_USER_CREATE, null);
        return toUserAdminVO(authMapper.findById(user.getId()));
    }

    /**
     * 用户删除预检查:聚合所有业务引用,返回明细供前端展示 CTA 与阻断原因。
     * 仅校验账号存在且为普通用户;不抛业务阻断异常。
     */
    public UserDeleteCheckVO getUserDeleteCheck(Long userId) {
        requireOrdinaryUser(userId);
        return collectDeleteCheck(userId);
    }

    /**
     * 物理删除用户(硬删除)。只有零业务数据的"干净账号"才能删除,
     * 否则抛 {@link ErrorCode#USER_HAS_BUSINESS_DATA} 引导管理员改用 {@link #disableUser}。
     * 应用层先拦给出友好错误,并发兜底由 ticket_order / passenger 等表的 FK RESTRICT 保证。
     */
    @Transactional
    public void deleteUser(Long userId) {
        Long adminUserId = currentAdminId();
        User user = requireOrdinaryUser(userId);
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        UserDeleteCheckVO check = collectDeleteCheck(userId);
        if (!check.canDelete()) {
            throw new BusinessException(ErrorCode.USER_HAS_BUSINESS_DATA);
        }
        authMapper.deleteUserById(userId);
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_USER, userId,
                AdminOperationLogService.ACTION_USER_DELETE, null);
    }

    @Transactional
    public void disableUser(Long userId) {
        Long adminUserId = currentAdminId();
        User user = authMapper.findById(userId);
        if (user == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if ("ADMIN".equals(user.getRole())) throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        if (!"NORMAL".equals(user.getStatus())) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        int updated = authMapper.updateStatusCAS(userId, "NORMAL", "DISABLED");
        if (updated == 0) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        operationLogService.log(adminUserId, AdminOperationLogService.TARGET_USER, userId,
                AdminOperationLogService.ACTION_USER_DISABLE, null);
    }

    @Transactional
    public void enableUser(Long userId) {
        User user = authMapper.findById(userId);
        if (user == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if ("ADMIN".equals(user.getRole())) throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        int updated = authMapper.updateStatusCAS(userId, "DISABLED", "NORMAL");
        if (updated == 0) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        operationLogService.log(currentAdminId(), AdminOperationLogService.TARGET_USER, userId,
                AdminOperationLogService.ACTION_USER_ENABLE, null);
    }

    public PageResponse<OrderVO> listOrders(int page, int size) {
        AdminOrderQueryDTO query = new AdminOrderQueryDTO();
        query.setPage(page);
        query.setSize(size);
        return listOrders(query);
    }

    public PageResponse<OrderVO> listOrders(AdminOrderQueryDTO query) {
        validateOrderQuery(query);
        int offset = AdminListQuerySupport.offset(query);
        List<OrderVO> orders = orderMapper.searchOrdersAdmin(query, offset, query.getSize());
        long total = orderMapper.countOrdersAdmin(query);
        return new PageResponse<>(orders, total, query.getPage(), query.getSize());
    }

    public OrderVO getOrderDetail(Long id) {
        OrderVO order = orderMapper.findDetailById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private User requireOrdinaryUser(Long userId) {
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
        return user;
    }

    /**
     * 硬删除预检查:统计所有业务引用(FK RESTRICT 不分状态),聚合成可读的阻断原因。
     */
    private UserDeleteCheckVO collectDeleteCheck(Long userId) {
        int orders = authMapper.countAllOrdersByUserId(userId);
        int passengers = authMapper.countAllPassengersByUserId(userId);
        int waitlist = authMapper.countAllWaitlistByUserId(userId);
        int refundOrChange = authMapper.countAllRefundOrChangeByUserId(userId);
        boolean oauthBound = authMapper.existsOauthBindingByUserId(userId);
        int aiSessions = authMapper.countAiChatSessionsByUserId(userId);
        int aiRecommendations = authMapper.countAiRecommendationsByUserId(userId);

        List<String> blockReasons = new ArrayList<>();
        if (orders > 0) blockReasons.add("存在 " + orders + " 笔订单记录");
        if (passengers > 0) blockReasons.add("存在 " + passengers + " 个乘机人记录");
        if (waitlist > 0) blockReasons.add("存在 " + waitlist + " 条候补记录");
        if (refundOrChange > 0) blockReasons.add("存在 " + refundOrChange + " 条退款/改签记录");
        if (oauthBound) blockReasons.add("已绑定第三方登录账号");
        if (aiSessions > 0) blockReasons.add("存在 " + aiSessions + " 条 AI 对话记录");
        if (aiRecommendations > 0) blockReasons.add("存在 " + aiRecommendations + " 条 AI 推荐记录");

        return new UserDeleteCheckVO(blockReasons.isEmpty(), orders, passengers, waitlist,
                refundOrChange, oauthBound, aiSessions, aiRecommendations, blockReasons);
    }

    private Long currentAdminId() {
        return operationLogService.currentAdminId();
    }

    private void validateOrderQuery(AdminOrderQueryDTO query) {
        AdminListQuerySupport.normalize(query);
        validateEnum(query.getStatus(), VALID_ORDER_STATUSES);
        validateDate(query.getDepartureDateStart());
        validateDate(query.getDepartureDateEnd());
    }

    private void validateEnum(String value, Set<String> valid) {
        if (value != null && !value.isBlank() && !valid.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateDate(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private UserAdminVO toUserAdminVO(User user) {
        return new UserAdminVO(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getNickname(),
                user.getRealName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                user.getEmailVerified(),
                user.getPhoneVerified(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
