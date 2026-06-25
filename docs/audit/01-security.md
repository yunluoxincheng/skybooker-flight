# 安全审查报告

> **📌 状态(2026-06-25):本报告所列 CRITICAL + HIGH 已全部处理**(修复或评估关闭)。统一追踪与逐项 PR 映射见 [汇总报告 · 修复状态总览](2026-06-24-backend-deep-review.md#修复状态总览)。以下为审查时点(2026-06-24)的原始记录,保留作回溯。

- **维度**:认证/授权、IDOR/越权、SQL 注入、输入校验、限流/防刷、敏感信息、CSRF/CORS
- **审查方**:security-reviewer
- **日期**:2026-06-24

依据文档:`docs/15_AUTH_DESIGN.md`、`docs/07_API_DESIGN.md`、`appendices/error-code.md`、`appendices/api-response-convention.md`。

## CRITICAL

### C-1 默认管理员账号使用可预测的弱口令且未强制首改
- **文件**:`backend/src/main/resources/db/migration/V2__init_base_data.sql:3`
- **证据**:`('admin@skybooker.local', NULL, '$2b$12$1nvx/dJbiwwV6AckTZq.KeJlLyLnsjo0y9UkfKrdcdOQCqVJwRA1S', ...)`,注释明确 `管理员 Admin@123456`
- **风险**:密码哈希是公开仓库的一部分,BCrypt 可被离线爆破;`Admin@123456` 是常见弱口令,可直接登录 `/api/admin/auth/login` 获得后台全部权限。`AuthController` 登录无 IP 级限流(仅验证码发送有限流),可被暴力试探。文档第 160 行要求"默认管理员密码必须在首次部署后修改",但代码无强制首改机制。
- **建议**:① 默认密码改为高熵随机值,部署时一次性注入(`ADMIN_INIT_PASSWORD`);② `/api/admin/auth/login` 增加 IP 维度失败计数锁定;③ 增加首登强制改密标记。

## HIGH

### H-1 管理员可禁用自身,导致锁死后台
- **文件**:`backend/src/main/java/com/skybooker/admin/service/AdminService.java:30-35`
- **证据**:`disableUser` 仅检查 `role != ADMIN`,未校验 `userId == 当前登录管理员 userId`。
- **风险**:违反 `07_API_DESIGN.md:621`;管理员传自己的 users.id 即可把自己置为 DISABLED,若只有一个管理员则永久锁死后台。
- **建议**:`disableUser`/`enableUser` 读取 `SecurityUtil.getCurrentPrincipal().userId()`,等于目标 userId 时抛 `ADMIN_ACCOUNT_PROTECTED`。

### H-2 登录接口无频率限制/失败锁定
- **文件**:`auth/service/AuthService.java:38-62`、`admin/service/AdminAuthService.java:28-62`
- **证据**:用户/管理员登录均无失败计数、IP 限流、账号锁定;唯一限流只在验证码发送链路。
- **风险**:`/api/auth/login` 和 `/api/admin/auth/login` 可被无限次暴力破解(凭证填充);配合 C-1,管理员弱口令极易被命中。
- **建议**:对两个登录接口按账号 + IP 维度统计失败次数,N 次失败后锁定(如 5 次锁 15 分钟),复用 Redis。

### H-3 订单创建/支付存在并发 TOCTOU 与状态机非 CAS
- **文件**:`order/service/OrderService.java:41-83, 102-131`
- **证据**:`payOrder`(line 127)用普通 `updateOrderStatus`,不是 CAS,依赖前置内存状态判断。
- **风险**:两个并发支付请求都能通过 `PENDING_PAYMENT` 判断,绕过 CAS 意图(详见汇总报告 C1 / 并发报告 C1)。
- **建议**:`payOrder` 改用 `updateOrderStatusCAS(orderId, "PENDING_PAYMENT", "ISSUED")`,败者返回当前 ISSUED(幂等)。

### H-4 找回密码/注册场景邮箱存在性信息泄露
- **文件**:`auth/service/AuthService.java:76-87`
- **证据**:`RESET_PASSWORD` 对不存在邮箱抛 `RESOURCE_NOT_FOUND`;`REGISTER` 对已存在抛 `EMAIL_ALREADY_REGISTERED`。
- **风险**:攻击者可枚举系统已注册邮箱(OWASP 账号枚举)。
- **建议**:`RESET_PASSWORD` 无论邮箱是否存在都返回统一"若已注册,验证码已发送",仅在存在时落库发送。

### H-5 AI 接口无任何频率限制,且匿名可调用 LLM
- **文件**:`ai/controller/AiController.java:19-23`、`config/SecurityConfig.java:52-54`
- **证据**:`/api/ai/**` 允许匿名;`chat` 每次触发外部 LLM 请求(`OpenAiCompatibleLlmChatClient`)。
- **风险**:未认证用户可无限次调用,直接消耗 LLM 配额(财务 DoS);无单 IP/单会话频率限制。
- **建议**:`/api/ai/chat` 加 IP 维度限流(如每分钟 10 次);匿名会话限制消息数与每日上限。

## MEDIUM

### M-1 每请求查库校验账号状态,放大 DB 压力
- **文件**:`common/security/JwtAuthenticationFilter.java:75-99`
- **证据**:`isAccountActive` 每个携带 JWT 的请求都 `authMapper.findById`,管理员还 `adminMapper.findByUserId`。
- **建议**:引入短 TTL(30-60s)账号状态缓存,或禁用时维护 JWT 黑名单/版本号。

### M-2 LogMailService 在生产可能打印验证码明文
- **文件**:`auth/mail/LogMailService.java:10`
- **证据**:`log.info("[MailService] 发送验证码: ... code={}", code)`;application.yml 默认 `MAIL_PROVIDER=log`。
- **风险**:生产误配 log 模式,验证码明文入日志,可被重置任意账号。
- **建议**:LogMailService 加 `@Profile({"dev","test"})`,或启动时校验 prod + log 拒绝启动。

### M-3 ORDER BY 使用 `${}` 拼接(模式隐患)
- **文件**:`mapper/flight/FlightMapper.xml:128, 196`
- **证据**:`ORDER BY ${orderBy}`;当前经 `FlightSort` 枚举白名单映射安全,但 `FlightSearchDTO.sort` 是裸 String 无 `@Pattern`。
- **建议**:`sort` 加 `@Pattern(regexp="DEFAULT|COMPREHENSIVE|PRICE_ASC|DURATION_ASC|TIME_ASC|SEATS_DESC|PUNCTUAL_DESC")`,或改 `<choose>` 固定列名。

### M-4 logout 未使 token 失效
- **文件**:`auth/controller/AuthController.java:50-53`、`admin/controller/AdminAuthController.java:29-32`
- **证据**:logout 仅返回 success,无黑名单/吊销。
- **建议**:Redis 维护 JWT 黑名单(logout 写入 jti + 剩余 TTL),`JwtAuthenticationFilter` 校验时检查。

### M-5 JWT 默认 24h 偏长,无 Refresh Token
- **文件**:`application.yml:29`
- **证据**:`expiration: ${JWT_EXPIRATION:86400000}`(24h)。
- **建议**:access 缩短到 1-2h + refresh token,或下调默认值并接黑名单。

### M-6 改签可跨舱等,金额漏洞
- **文件**:`change/service/ChangeService.java:227-240`
- **证据**:`validateNewSeats` 只校验航班与 AVAILABLE,未校验 `cabinClass`/`seatType` 一致;`change_fee` 不计入 `total_amount`。
- **风险**:用户可用经济舱票价 + 0 手续费占商务舱/头等舱座位。
- **建议**:校验新座舱等与原订单一致,或按舱差补差价计入 total_amount。

### M-7 DB 连接 `useSSL=false` + `allowPublicKeyRetrieval=true`
- **文件**:`application.yml:6`
- **风险**:跨网部署凭证与查询可被嗅探。
- **建议**:生产强制 `useSSL=true` 并校验证书;仅本地保留 false。

## LOW

- **L-1** 订单号/候补号用 `java.util.Random` 可预测(`OrderService.java:233` 等)→ 改 `SecureRandom`。
- **L-2** `GlobalExceptionHandler` 兜底未 `log.error`,异常现场丢失(`GlobalExceptionHandler.java:68-72`)。
- **L-3** `FlightSearchDTO` 字符串字段无 `@Size` 上限。
- **L-4** CORS 未显式配置(依赖 nginx 代理)。
- **L-5** `AiChatRequest.message` 仅 `@NotBlank` 无 `@Size(max=...)`,放大 LLM 成本。

## 检查项小结

| 检查项 | 结论 |
|---|---|
| 认证/授权(角色隔离) | 基本到位。问题:管理员可禁用自身(H-1)、登录无限流(H-2) |
| IDOR/越权 | 未发现。所有 service 校验 `order.getUserId().equals(userId)` 等 |
| SQL 注入 | 未发现可利用注入。`${}` 仅 ORDER BY + 枚举白名单 |
| 输入校验 | 大部分到位;部分列表 `size` 无上限 |
| 限流/防刷 | 仅验证码发送有完整限流;登录、下单、AI、改签、退款均无限流 |
| 敏感信息 | C-1 默认哈希入库;M-2 验证码明文日志;M-7 useSSL=false;其余良好 |
| CSRF/CORS/反序列化/上传 | CSRF 已禁用(合理);无上传;CORS 未显式配置 |

## 总体安全评估

鉴权设计(双 portal 隔离)、IDOR 防护、SQL 注入防护、并发座位锁(CAS + 状态机)做得扎实。但 **1 个 CRITICAL(默认管理员弱口令)** 必须立即处理,H-2(登录无限流)、H-1(自禁用)、H-5(AI 无限流)三个 HIGH 须本迭代闭环。M-2、M-3 纳入整改计划。
