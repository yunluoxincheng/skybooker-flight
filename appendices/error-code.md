# 错误码规范

> 与 `backend/src/main/java/com/skybooker/common/exception/ErrorCode.java` 对齐。
> HTTP 状态由 `GlobalExceptionHandler` 按错误类型映射。

## HTTP 状态码

| HTTP | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数 / 业务校验失败 |
| 401 | 未认证(Token 无效 / 过期 / 邮箱密码错误) |
| 403 | 无权限(角色不符 / 账号禁用) |
| 404 | 资源不存在 |
| 429 | 限流(登录失败次数过多) |
| 500 | 系统异常 |

## 业务错误码

| 码 | 名称 | 说明 | HTTP |
|---|---|---|---|
| 10001 | UNAUTHORIZED | 用户未登录 | 401 |
| 10002 | FORBIDDEN | 用户无权限 | 403 |
| 10003 | VALIDATION_ERROR | 参数校验失败 | 400 |
| 10004 | VERIFICATION_CODE_INVALID | 验证码无效或已过期 | 400 |
| 10005 | VERIFICATION_CODE_SEND_TOO_FREQUENT | 验证码发送过于频繁 | 400 |
| 10006 | VERIFICATION_CODE_DAILY_LIMIT | 验证码发送次数已达每日上限 | 400 |
| 10007 | INVALID_CREDENTIALS | 邮箱或密码错误 | 401 |
| 10008 | ACCOUNT_DISABLED | 账号已被禁用 | 403 |
| 10009 | EMAIL_ALREADY_REGISTERED | 邮箱已注册 | 400 |
| 10010 | PASSWORD_MISMATCH | 两次密码输入不一致 | 400 |
| 10011 | TOKEN_EXPIRED | Token 已失效 | 401 |
| 10012 | ACCOUNT_TYPE_MISMATCH | 账号类型不允许登录当前入口 | 403 |
| 10013 | IP_CODE_LIMIT_EXCEEDED | IP 验证码请求次数已达上限 | 400 |
| 10014 | VERIFICATION_CODE_MAX_ATTEMPTS | 验证码错误次数过多 | 400 |
| 10015 | SCENE_NOT_SUPPORTED | 不支持的场景类型 | 400 |
| 10016 | VERIFICATION_EMAIL_SEND_FAILED | 验证码邮件发送失败 | 400 |
| 10017 | LOGIN_RATE_LIMITED | 登录失败次数过多 | 429 |
| 10018 | TOKEN_INVALID | Token 无效 | 401 |
| 10019 | ADMIN_PROFILE_DISABLED | 管理员账号已被禁用 | 403 |
| 10020 | AI_RATE_LIMITED | 请求过于频繁，请稍后再试 | 429 |
| 10021 | REFRESH_TOKEN_INVALID | 刷新令牌无效或已过期 | 401 |
| 10022 | AI_LLM_CONFIG_INVALID | LLM 配置校验失败 | 400 |
| 20001 | RESOURCE_NOT_FOUND | 资源不存在 | 404 |
| 30001 | FLIGHT_NOT_SELLABLE | 航班不可预订 | 400 |
| 30002 | SEAT_NOT_AVAILABLE | 座位不可用 | 400 |
| 30003 | SEAT_LOCK_FAILED | 座位锁定失败 | 400 |
| 40001 | ORDER_STATE_INVALID | 订单状态不允许此操作 | 400 |
| 40002 | ORDER_EXPIRED | 订单已过期 | 400 |
| 40003 | SEAT_ALREADY_EXISTS | 航班座位已存在 | 400 |
| 40004 | PASSENGER_HAS_ORDERS | 乘机人有订单记录，无法删除 | 400 |
| 40005 | DUPLICATE_PASSENGER | 乘机人证件号重复 | 400 |
| 40006 | DUPLICATE_SEAT_IN_ORDER | 订单中存在重复座位 | 400 |
| 40007 | DUPLICATE_PASSENGER_IN_ORDER | 订单中存在重复乘机人 | 400 |
| 40008 | FLIGHT_HAS_INVENTORY | 航班已有座位或订单，不允许修改 | 400 |
| 40009 | ADMIN_ACCOUNT_PROTECTED | 不允许操作管理员账号 | 400 |
| 50001 | REFUND_WINDOW_CLOSED | 退款窗口已关闭，距起飞不足 2 小时 | 400 |
| 50002 | WAITLIST_NOT_FOUND | 候补订单不存在 | 400 |
| 50003 | WAITLIST_STATE_INVALID | 候补订单状态不允许此操作 | 400 |
| 50004 | WAITLIST_NOT_NEEDED | 当前舱位余票充足，无需候补 | 400 |
| 50005 | DUPLICATE_WAITLIST_PASSENGER | 候补订单中存在重复乘机人 | 400 |
| 50006 | CHANGE_WINDOW_CLOSED | 改签窗口已关闭，距起飞不足 2 小时 | 400 |
| 90000 | SYSTEM_ERROR | 系统异常 | 500 |

## 码段划分

- `10001-10022`:认证 / 验证码 / 限流 / Token / AI 配置校验
- `20001`:通用资源
- `30001-30003`:航班 / 座位
- `40001-40009`:订单 / 乘机人 / 座位业务
- `50001-50005`:退款 / 候补
- `90000`:系统

> 新增业务码时按所属码段递增,并在本表与 `ErrorCode.java` 同步登记,禁止复用已有码值。
