package com.skybooker.admin.service;

import com.skybooker.admin.entity.AdminOperationLog;
import com.skybooker.admin.mapper.AdminOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminOperationLogService {

    public static final String TARGET_ORDER = "ORDER";
    public static final String TARGET_USER = "USER";

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

    private final AdminOperationLogMapper adminOperationLogMapper;

    public void log(Long adminUserId, String targetType, Long targetId, String action, String reason) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(adminUserId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setReason(reason);
        adminOperationLogMapper.insertLog(log);
    }
}
