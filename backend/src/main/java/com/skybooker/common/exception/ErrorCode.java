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
    INVALID_CREDENTIALS(10007, "账号或密码错误"),
    ACCOUNT_DISABLED(10008, "账号已被禁用"),
    ACCOUNT_TYPE_MISMATCH(10012, "账号类型不允许登录当前入口"),
    TOKEN_INVALID(10018, "Token 无效"),
    REFRESH_TOKEN_INVALID(10021, "刷新令牌无效或已过期"),
    TOKEN_EXPIRED(10011, "Token 已失效"),
    VERIFICATION_CODE_INVALID(10004, "验证码无效或已过期"),
    VERIFICATION_CODE_SEND_TOO_FREQUENT(10005, "验证码发送过于频繁"),
    VERIFICATION_CODE_DAILY_LIMIT(10006, "验证码发送次数已达每日上限"),
    EMAIL_ALREADY_REGISTERED(10009, "邮箱已注册"),
    PASSWORD_MISMATCH(10010, "两次密码输入不一致"),

    // Validation
    VALIDATION_ERROR(10003, "参数校验失败"),

    // Admin
    ADMIN_PROFILE_DISABLED(10019, "管理员账号已被禁用"),

    // Business
    RESOURCE_NOT_FOUND(20001, "资源不存在"),
    FLIGHT_NOT_SELLABLE(30001, "航班不可预订"),
    SEAT_NOT_AVAILABLE(30002, "座位不可用"),
    SEAT_LOCK_FAILED(30003, "座位锁定失败"),
    ITINERARY_INVALID(30004, "联程行程已失效，请重新搜索"),
    INVALID_CONNECTION(30005, "中转机场或中转时间不符合要求"),
    INCOMPLETE_SEGMENT_SEATS(30006, "每位乘机人必须在每个航段选择座位"),
    IDEMPOTENCY_CONFLICT(30007, "请求标识已被其他操作使用"),
    CONNECTING_ITINERARY_ALREADY_EXISTS(30008, "该两段航班已存在联程方案，请勿重复创建"),
    ORDER_STATE_INVALID(40001, "订单状态不允许此操作"),
    ORDER_EXPIRED(40002, "订单已过期"),
    SEAT_ALREADY_EXISTS(40003, "航班座位已存在"),
    PASSENGER_HAS_ORDERS(40004, "乘机人有订单记录，无法删除"),
    DUPLICATE_PASSENGER(40005, "乘机人证件号重复"),
    DUPLICATE_SEAT_IN_ORDER(40006, "订单中存在重复座位"),
    DUPLICATE_PASSENGER_IN_ORDER(40007, "订单中存在重复乘机人"),
    IP_CODE_LIMIT_EXCEEDED(10013, "IP验证码请求次数已达上限"),
    VERIFICATION_CODE_MAX_ATTEMPTS(10014, "验证码错误次数过多，请重新获取"),
    SCENE_NOT_SUPPORTED(10015, "不支持的场景类型"),
    VERIFICATION_EMAIL_SEND_FAILED(10016, "验证码邮件发送失败，请稍后重试"),
    LOGIN_RATE_LIMITED(10017, "登录失败次数过多，请稍后再试"),
    AI_RATE_LIMITED(10020, "请求过于频繁，请稍后再试"),
    FLIGHT_HAS_INVENTORY(40008, "航班已有座位、订单或舱位配置，不允许修改"),
    ADMIN_ACCOUNT_PROTECTED(40009, "不允许操作管理员账号"),
    DUPLICATE_AIRLINE_CODE(40010, "航司代码已存在"),
    DUPLICATE_AIRPORT_CODE(40011, "机场代码已存在"),
    AIRLINE_IN_USE(40012, "航司存在关联航班，无法删除"),
    AIRPORT_IN_USE(40013, "机场存在关联航班，无法删除"),
    ORDER_NOT_VOIDABLE(40020, "订单当前状态不可作废"),
    USER_HAS_ACTIVE_ORDERS(40021, "用户存在未完成订单，无法删除或禁用"),
    USER_HAS_PENDING_WAITLIST(40022, "用户存在进行中候补，无法删除或禁用"),
    USER_HAS_PROCESSING_REFUND_OR_CHANGE(40023, "用户存在处理中退票或改签，无法删除或禁用"),
    USER_HAS_BUSINESS_DATA(40024, "用户存在业务记录，无法删除，请改用禁用"),
    REFUND_WINDOW_CLOSED(50001, "退款窗口已关闭，距起飞不足2小时"),
    WAITLIST_NOT_FOUND(50002, "候补订单不存在"),
    WAITLIST_STATE_INVALID(50003, "候补订单状态不允许此操作"),
    WAITLIST_NOT_NEEDED(50004, "当前舱位余票充足，无需候补"),
    DUPLICATE_WAITLIST_PASSENGER(50005, "候补订单中存在重复乘机人"),
    CHANGE_WINDOW_CLOSED(50006, "改签窗口已关闭，距起飞不足2小时"),
    CHANGE_FLIGHT_EARLIER_THAN_ORIGINAL(50007, "改签航班出发时间需晚于原航班出发时间至少2小时"),
    AI_LLM_CONFIG_INVALID(10022, "LLM 配置校验失败");

    private final int code;
    private final String message;
}
