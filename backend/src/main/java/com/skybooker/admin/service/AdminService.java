package com.skybooker.admin.service;

import com.skybooker.admin.dto.AdminCreateUserDTO;
import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.admin.vo.UserAdminVO;
import com.skybooker.auth.entity.User;
import com.skybooker.auth.mapper.AuthMapper;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.common.security.SecurityUtil;
import com.skybooker.order.mapper.OrderMapper;
import com.skybooker.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuthMapper authMapper;
    private final OrderMapper orderMapper;
    private final AdminMapper adminMapper;
    private final AdminOperationLogService operationLogService;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<UserAdminVO> listUsers(int page, int size) {
        int offset = (page - 1) * size;
        List<UserAdminVO> users = authMapper.findUsersByRole("USER", offset, size);
        long total = authMapper.countUsersByRole("USER");
        return new PageResponse<>(users, total, page, size);
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

    @Transactional
    public void deleteUser(Long userId) {
        Long adminUserId = currentAdminId();
        User user = requireOrdinaryUser(userId);
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        validateNoActiveBusiness(userId);
        int updated = authMapper.updateStatusCAS(userId, user.getStatus(), "DELETED");
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
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
        validateNoActiveBusiness(userId);
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
        int offset = (page - 1) * size;
        List<OrderVO> orders = orderMapper.findAllOrders(offset, size);
        long total = orderMapper.countAllOrders();
        return new PageResponse<>(orders, total, page, size);
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

    private void validateNoActiveBusiness(Long userId) {
        if (authMapper.countActiveOrdersByUserId(userId) > 0) {
            throw new BusinessException(ErrorCode.USER_HAS_ACTIVE_ORDERS);
        }
        if (authMapper.existsPendingWaitlistByUserId(userId)) {
            throw new BusinessException(ErrorCode.USER_HAS_PENDING_WAITLIST);
        }
        if (authMapper.existsProcessingRefundOrChangeByUserId(userId)) {
            throw new BusinessException(ErrorCode.USER_HAS_PROCESSING_REFUND_OR_CHANGE);
        }
    }

    private Long currentAdminId() {
        AdminUser adminUser = adminMapper.findByUserId(SecurityUtil.getCurrentUserId());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return adminUser.getId();
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
