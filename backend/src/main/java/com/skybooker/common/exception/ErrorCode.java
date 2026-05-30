package com.skybooker.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // System
    SYSTEM_ERROR(90000, "系统异常"),

    // Auth
    UNAUTHORIZED(10001, "用户未登录"),
    FORBIDDEN(10002, "用户无权限"),
    INVALID_CREDENTIALS(10007, "邮箱或密码错误"),
    ACCOUNT_DISABLED(10008, "账号已被禁用"),
    ACCOUNT_TYPE_MISMATCH(10012, "账号类型不允许登录当前入口"),
    TOKEN_INVALID(10001, "Token 无效"),
    TOKEN_EXPIRED(10011, "Token 已失效"),

    // Validation
    VALIDATION_ERROR(10003, "参数校验失败"),

    // Admin
    ADMIN_PROFILE_DISABLED(10008, "管理员账号已被禁用"),

    // Business
    RESOURCE_NOT_FOUND(20001, "资源不存在");

    private final int code;
    private final String message;
}
