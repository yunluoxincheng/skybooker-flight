# 文档符合性与代码质量审查报告

- **维度**:接口契约、统一响应包络、错误码、分层职责、测试覆盖、输入校验、配置默认值
- **审查方**:code-reviewer
- **日期**:2026-06-24

依据文档:`docs/07_API_DESIGN.md`、`docs/10_BACKEND_DESIGN.md`、`docs/12_TESTING_GUIDE.md`、`appendices/api-response-convention.md`、`appendices/error-code.md`、`docs/16_STATE_MACHINE.md`。

## CRITICAL

### C1. 错误码体系与 `appendices/error-code.md` 严重不一致
- **文件**:`common/exception/ErrorCode.java`
- **证据**:文档定义 `10001-10012` 认证、`20001-20004` 航班/座位、`30001-30004` 订单、`40001-40004` 候补、`50001-50002` AI、`90000` 系统。代码完全偏离:
  - **码值冲突**:`TOKEN_INVALID` 与 `UNAUTHORIZED` 同 10001(行 14,19);`ADMIN_PROFILE_DISABLED` 与 `ACCOUNT_DISABLED` 同 10008(行 17,31)。
  - **语义错配**:`VERIFICATION_CODE_INVALID`=10004(文档 10004="邮箱已注册");`EMAIL_ALREADY_REGISTERED`=10009(文档 10009="两次密码不一致")。
  - **跨模块占用**:订单错误用 30001-30003(文档此处为订单但座位/航班被错搬到 30001-30003);候补用 50002-50005(文档 50001-50002 属 AI)。
  - **文档定义码缺失**:20002/20003/20004/30004/10006(文档语义)无对应项。
  - **未定义码新增**:10013-10016、40003-40009 不在 error-code.md。
- **风险**:前端按文档码值分支全部错配;对外契约不可信;同码多用致排障困难。
- **修复**:以 `appendices/error-code.md` 为准重排 ErrorCode;新增码同步文档;禁止复用已有码。

### C2. 退票接口签名与 API 文档不符(缺 `reason` 请求体)
- **文件**:`order/controller/OrderController.java:49-52`
- **证据**:`refundOrder(@PathVariable Long id)` 无 `@RequestBody`;文档 `07_API_DESIGN.md:319-338` 要求 `{"reason":"行程变更"}`;`RefundRecord` 实体已有 `reason` 字段但恒 null。
- **风险**:审计字段缺失;前端按文档传 body 被静默忽略。
- **修复**:新增 `RefundDTO{reason}` + `@Valid @RequestBody`,透传 RefundService 写入。

## HIGH

### H1. 订单创建并发安全不足,且无并发测试
- **文件**:`order/service/OrderService.java:40-100`
- **证据**:`createOrder` 虽 `@Transactional`,但 `validateSeats`(读)与 `lockSeats`(CAS)之间有窗口;`decrementRemainingSeats` 失败时回滚依赖事务。**测试套件无任何订单创建并发场景**(仅 `ChangeIntegrationTest` 测了改签并发)。
- **风险**:高并发重复下单可能产生两条 PENDING 订单锁定同座(取决于 lockSeats SQL 条件),且测试未覆盖。
- **修复**:补并发集成测试(CountDownLatch 模拟抢同座,断言仅一条成功);mapper XML 确认 lockSeats 带 `AND status='AVAILABLE'` 并加注释。

### H2. `ChangeService` 用 `REFUND_WINDOW_CLOSED`(50001) 表示改签截止
- **文件**:`change/service/ChangeService.java:189`
- **证据**:`validateChangeCutoff` 抛 `REFUND_WINDOW_CLOSED`(message="退款窗口已关闭,距起飞不足2小时")。
- **风险**:改签场景用户看到"退款"字样,与 `16_STATE_MACHINE` 改签语义不符;错误码语义被污染。
- **修复**:新增 `CHANGE_WINDOW_CLOSED` 错误码(并在 error-code.md 登记)。

### H3. `GlobalExceptionHandler` 兜底未记录日志
- **文件**:`common/exception/GlobalExceptionHandler.java:68-72`
- **证据**:`handleException(Exception e)` 只返回 `SYSTEM_ERROR`,未 `log.error(e)`;`BusinessException` 处理也未记录。
- **风险**:线上 500 无线索;违反 `10_BACKEND_DESIGN` "服务端记录详细错误上下文"。
- **修复**:通用 Exception 与 BusinessException handler 加 `log.error(...)`(业务 debug 级,系统 error 级)。

### H4. JWT 无登出失效机制
- **文件**:`auth/controller/AuthController.java:50-53`、`admin/controller/AdminAuthController.java:29-32`
- **证据**:logout 仅返回 success;`JwtTokenProvider` 无黑名单;STATELESS 下 token 过期前(默认 24h)始终有效。
- **风险**:登出后 token 仍可用,账户被盗无法即时止损。
- **修复**:Redis token 黑名单(写入 jti + 剩余 TTL),或文档明确"登出仅前端清理"并降低安全承诺。

## MEDIUM

- **M1** 测试覆盖盲区(对照 12_TESTING_GUIDE):① 订单创建并发为 0;② 支付/取消并发幂等未测;③ Flyway seed 账号密码哈希未断言;④ 禁用当前登录管理员自身未确认覆盖。
- **M2** `AdminService.disableUser` 未阻止禁用当前登录管理员自身(仅查 role=ADMIN)— `admin/service/AdminService.java:30-40`。加 `userId == currentUserId` 校验。
- **M3** `FlightSearchDTO` 含文档未定义参数 `passengerCount`、`cabinClass`(:33-34)— 更新 07_API_DESIGN 或移除。
- **M4** 测试 `application.yml` JWT secret 写死,`application.yml` 与 `application-test.yml` 双份配置可能漂移 — 确认统一走 test profile。

## LOW

- **L1** `RESOURCE_NOT_FOUND`(404) 部分语义实为"无权限"伪装(信息隐藏设计合理,07_API_DESIGN 未明确)— api-response-convention 补说明。
- **L2** `OrderService.generateOrderNo` 用 `new Random()` 非线程安全(:233)— 改 ThreadLocalRandom 或类级 SecureRandom。
- **L3** `MAIL_FROM` 默认空字符串,Resend 模式下忘配会运行时失败 — `MailConfiguration` 启动校验 provider=resend 时 from 非空(与 provider 非法值 fail-fast 风格一致)。

## Review Summary

| Severity | Count |
|---|---|
| CRITICAL | 2 |
| HIGH | 4 |
| MEDIUM | 4 |
| LOW | 3 |

**Verdict: BLOCK** — 2 个 CRITICAL(错误码体系偏离、退票缺 reason)必须修复。

## 文档符合性总体评估

分层架构(Controller/Service/Mapper)、统一响应包络 `ApiResponse{code,message,data}`、分页结构 `{records,total,page,size}`、`/api` 前缀、AI sessionId 用 UUID、admin 用户列表强制 role=USER、双 portal 鉴权隔离——**核心契约与文档高度一致**,测试覆盖(23 类)较好覆盖状态机与角色隔离。

最大缺口是**错误码体系**:`ErrorCode.java` 码值分配与 `appendices/error-code.md` 几乎完全错位(同码多用、跨模块占用、文档定义码缺失、未定义码随意新增),导致对外错误码契约不可信。其次若干接口细节漂移(退票缺 reason、航班搜索多 passengerCount/cabinClass、改签复用退款错误码)。建议优先以 error-code.md 为准做错误码对齐,并补并发与 seed 账号测试。
