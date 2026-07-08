package com.skybooker.admin.service;

import com.skybooker.admin.entity.AdminOperationLog;
import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.admin.mapper.AdminOperationLogMapper;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminOperationLogService {

    public static final String TARGET_ORDER = "ORDER";
    public static final String TARGET_USER = "USER";
    public static final String TARGET_AIRLINE = "AIRLINE";
    public static final String TARGET_AIRPORT = "AIRPORT";

    public static final String ACTION_ORDER_CREATE = "ORDER_CREATE";
    public static final String ACTION_REFUND = "REFUND";
    public static final String ACTION_REFUND_FORCE = "REFUND_FORCE";
    public static final String ACTION_CHANGE = "CHANGE";
    public static final String ACTION_CHANGE_FORCE = "CHANGE_FORCE";
    public static final String ACTION_VOID = "VOID";
    public static final String ACTION_ADMIN_NOTE_UPDATE = "ADMIN_NOTE_UPDATE";
    public static final String ACTION_USER_CREATE = "USER_CREATE";
    public static final String ACTION_USER_DELETE = "USER_DELETE";
    public static final String ACTION_USER_DISABLE = "USER_DISABLE";
    public static final String ACTION_USER_ENABLE = "USER_ENABLE";
    public static final String ACTION_AIRLINE_DELETE = "AIRLINE_DELETE";
    public static final String ACTION_AIRPORT_DELETE = "AIRPORT_DELETE";

    private final AdminOperationLogMapper adminOperationLogMapper;
    private final AdminMapper adminMapper;

    public void log(Long adminUserId, String targetType, Long targetId, String action, String reason) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(adminUserId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setReason(reason);
        adminOperationLogMapper.insertLog(log);
    }

    /**
     * 解析当前认证上下文对应的管理员账号 id。集中在此处供各 admin service 复用,
     * 避免 currentAdminId 逻辑散落多处产生重复。
     */
    public Long currentAdminId() {
        AdminUser adminUser = adminMapper.findByUserId(SecurityUtil.getCurrentUserId());
        if (adminUser == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return adminUser.getId();
    }
}
