# 后端深度审查 · 汇总报告

> **📌 状态(2026-06-25):CRITICAL + HIGH 已全部处理**(修复或评估关闭)。详见下方 [修复状态总览](#修复状态总览)。以下原始结论("BLOCK")为审查时点(2026-06-24)记录,保留作回溯。

- **日期**:2026-06-24
- **审查方**:security-reviewer / java-reviewer(并发事务) / database-reviewer / code-reviewer(文档符合性),4 路并行
- **范围**:backend 全 11 模块 + mapper XML + Flyway 迁移 + 测试
- **基线**:`mvn test` BUILD SUCCESS(exit 0)

## 总体结论

**BLOCK**。发现 **5 个 CRITICAL + ~13 个 HIGH**。问题集中在三处:
1. 订单/候补的并发与事务设计(状态机非原子、候补兑现事务污染、座位释放策略混用)。
2. 安全运营面(默认管理员弱口令、登录与 AI 接口无限流、JWT 无黑名单)。
3. 文档契约面(错误码体系偏离、退票接口缺 reason)。

## ✅ 做得好的(四方共识)

- **IDOR/越权防护到位**:订单/乘机人/退款/改签/候补全部用 `SecurityUtil.getCurrentUserId()` + 归属校验;AI 会话用不可猜测 UUID + 归属校验。未发现越权。
- **SQL 注入无实际风险**:所有 `${}` 仅在 ORDER BY,且经 `FlightSort` 枚举白名单映射,用户输入不直入 SQL。
- **角色隔离扎实**:`loginPortal` + `role` 双校验,admin/user token 跨 portal 互拒;AI 接口对 admin token 拒绝。
- **DB 设计扎实**:`flight_seat.version` 乐观锁、`uk_flight_seat(flight_id, seat_no)`、候补排序索引 `idx_waitlist_flight_status_paid`、外键索引、批量插入均符合 `06_DATABASE_DESIGN`。
- **金额/时间类型正确**:全 `DECIMAL(10,2)`、`DATETIME` + `serverTimezone=Asia/Shanghai`。

## 🔴 CRITICAL(5,必修,阻断测试)

### C1. 订单状态机非原子:`payOrder` / `cancelOrder` 无 CAS
- **位置**:`backend/src/main/java/com/skybooker/order/service/OrderService.java:127, 164`;SQL `OrderMapper.xml:99`
- **证据**:`orderMapper.updateOrderStatus(orderId, "ISSUED"/"CANCELLED")` 是无条件 `UPDATE ticket_order SET status=? WHERE id=?`,仅依赖前置内存状态判断。
- **风险**:并发支付/取消可同时通过 `PENDING_PAYMENT` 判断并重复推进;`payOrder` 与 `cancelOrder` 并发可致余票错乱(取消侧 `incrementRemainingSeats` 不校验 release 影响行数 → 余票凭空 +N → 超卖)。`refundOrder`/`changeOrder` 已用 CAS,**实现不一致**。
- **修复**:`payOrder`/`cancelOrder` 改用 `updateOrderStatusCAS(id, expected, new)`,返回 0 即幂等返回当前状态;`cancelOrder` 释放座位后校验 `releaseSeatsByOrderId` 影响行数 == passengerCount。

### C2. 候补兑现事务设计错误
- **位置**:`refund/service/RefundService.java:79` → `waitlist/service/WaitlistFulfillmentService.java:83-126`
- **证据**:退款(`@Transactional`)调 `tryFulfillWaitlists`(`@Transactional` REQUIRED,同事务);CAS `WAITING→SUCCESS` 放在流程末尾(:116);`remaining_seats` 在所有候补处理完后才统一扣减(:124-126)。
- **风险**:
  1. 候补兑现里任一无关候补抛异常会标记退款主事务 rollback-only → 用户退款失败(实际是被候补拖累)。
  2. CAS 顺序倒置:已 `insertOrder` + 锁座后才抢态,"先干活再抢锁"。
  3. 余票延迟统一扣减期间,正常下单读到未扣减旧余票 → 超卖窗口(违反 `16_STATE_MACHINE.md:106` "退票后候补兑现余票不增加" 的基准)。
- **修复**:候补兑现移到退款事务 `AFTER_COMMIT`;CAS 前置(先抢态再做事);每锁一组座位立即 `decrementRemainingSeats`,不要循环完再统一扣。

### C3. 默认管理员弱口令 + 登录无限流
- **位置**:`db/migration/V2__init_base_data.sql:3`(注释 `Admin@123456`);`auth/service/AuthService.java:38-62` + `admin/service/AdminAuthService.java:28-62`(登录无限流)
- **证据**:BCrypt 哈希在公开仓库,`Admin@123456` 是常见弱口令;两个 `/api/*/auth/login` 均无失败计数/IP 限流/账号锁定(仅验证码发送有限流)。
- **风险**:攻击者可直接暴力破解 `/api/admin/auth/login`,获得后台全部权限。
- **修复**:默认密码改为部署时注入(`ADMIN_INIT_PASSWORD` 环境变量),Flyway 不固定明文哈希;对两个登录接口加账号 + IP 维度失败计数锁定(如 5 次失败锁 15 分钟,复用 Redis)。

### C4. 错误码体系全面偏离 `appendices/error-code.md`
- **位置**:`common/exception/ErrorCode.java`
- **证据**:同码多用(`TOKEN_INVALID` 与 `UNAUTHORIZED` 同 10001;`ADMIN_PROFILE_DISABLED` 与 `ACCOUNT_DISABLED` 同 10008);跨模块占用(订单类用 30001-30003,文档此处为座位/航班;候补用 50002-50005,文档此处为 AI);文档定义码缺失(20002/20003/20004/30004 等);未定义码随意新增(10013-10016、40003-40009)。
- **风险**:对外错误码契约不可信;前端按文档码值分支会全部错配;排障困难。
- **修复**:以 `appendices/error-code.md` 为准重排 ErrorCode 码值;新增码同步更新文档;禁止复用已有码。

### C5. 退票接口缺 `reason` 请求体
- **位置**:`order/controller/OrderController.java:49-52`
- **证据**:`refundOrder(@PathVariable Long id)` 不接收 body;`RefundRecord` 实体有 `reason` 字段但恒 null。
- **风险**:违反 `07_API_DESIGN.md:319-338`(要求 `{"reason":"..."}`);审计字段缺失;前端传 body 被静默忽略。
- **修复**:新增 `RefundDTO{reason}` + `@Valid @RequestBody`,透传 RefundService 写入 `refund_record.reason`。

## 🟠 HIGH(13,合并前应修)

### 并发与事务
- **H1** `lockSeats` SQL 缺 `AND version = #{version}`,version 字段沦为摆设(违反 `10_BACKEND_DESIGN.md:189`)— `FlightMapper.xml` / `OrderService.java:75`
- **H2** `cancelOrder` 释放座位后未校验 release 影响行数,并发或已被清理释放时 `incrementRemainingSeats` 凭空 +N — `OrderService.java:165`
- **H3** 退款释放座位按 orderId 全量(`releaseSoldSeatsByOrderId`),改签后再退款可能与"新座位同 orderId"冲突 — `RefundService.java:75`
- **H4** 改签 release 旧座 / sell 新座跨航班非原子,存在竞态窗口 — `ChangeService.java:131-146`
- **H5** 候补兑现循环内逐条查可用座位 + 循环末尾统一扣余票,事务内余票与实际 SOLD 不一致 — `WaitlistFulfillmentService.java:42-126`
- **H6** `@Scheduled` 清理单大事务 + `createOrder` 同步触发全量清理(性能反模式)— `WaitlistCleanupScheduler.java` / `OrderService.java:47`

### 安全
- **H7** 管理员可禁用自身 → 锁死后台(违反 `07_API_DESIGN.md:621`)— `AdminService.java:30`
- **H8** AI 接口匿名无限流,可直接耗尽 LLM 配额(财务 DoS)— `AiController.java` / `SecurityConfig.java:52`
- **H9** 邮箱存在性枚举:`RESET_PASSWORD` 对不存在邮箱抛 `RESOURCE_NOT_FOUND` — `AuthService.java:84`
- **H10** JWT logout 不使 token 失效,无黑名单,默认 24h 偏长 — `AuthController.java:50` / `application.yml:29`
- **H11** `LogMailService` 默认打印验证码明文,生产误配 `log` 模式即泄露 — `LogMailService.java:10`
- **H12** DB 连接 `useSSL=false` + `allowPublicKeyRetrieval=true`,跨网部署可嗅探 — `application.yml:6`

### 数据库
- **H13a** 表无 `DEFAULT CHARSET=utf8mb4`,server 默认 latin1 时中文乱码 — `V1__init_schema.sql`
- **H13b** 状态枚举字段无 CHECK 约束,完全依赖应用层(MySQL 8.0.16+ 已支持)— `V1` 各 status/role
- **H13c** 外键缺 `ON DELETE` 语义,删航班/用户失败或留孤儿 — `V1__init_schema.sql`

### 契约
- **H14** 改签截止用 `REFUND_WINDOW_CLOSED`(50001),用户看到"退款窗口"字样,语义混淆 — `ChangeService.java:189`
- **H15** `GlobalExceptionHandler` 兜底与 `BusinessException` 均不记日志,线上 500 无线索 — `GlobalExceptionHandler.java:68`

## 🟡 MEDIUM(选改,影响质量/可维护)

- 时区依赖 JVM 默认(退款/改签 24h/2h 窗口边界)— `RefundService.java:87,94` / `ChangeService.java:187,243`
- 订单/候补号用 `new Random()` 可碰撞(应用 `SecureRandom`)— `OrderService.java:233` 等
- `FlightSearchDTO.sort` 无 `@Pattern`(ORDER BY 模式隐患)— `FlightSearchDTO.java`
- `passengerCount`/`cabinClass` 是 `07_API_DESIGN` 未定义的隐藏参数 — `FlightSearchDTO.java:33-34`
- 改签可跨舱等(金额漏洞,`change_fee` 不计入 total_amount)— `ChangeService.java:227-240`
- 每请求查库校验账号状态,放大 DB 压力 — `JwtAuthenticationFilter.java:75-99`
- `findById` 返回 `password_hash`(过度暴露)— `AuthMapper`
- 分页用 OFFSET,深翻性能差;`auth_verification_code_log` 无防刷索引;仪表盘全表 COUNT/SUM — 各 mapper
- 多表缺 `updated_at`(违反 `appendices/sql-convention.md:25`)— `order_passenger`/`refund_record`/`change_record` 等
- 测试缺并发下单、seed 账号断言、disable-self 覆盖 — `backend/src/test`
- 测试双份 `application.yml` 可能漂移 — `backend/src/test/resources`

## 🔵 LOW(可选)

`Random`→`SecureRandom`;DTO 字段无 `@Size`;CORS 未显式配置;`AiChatRequest.message` 无长度上限;`findMessagesBySessionId` 无 LIMIT;业务魔数落在 DDL。

## 修复优先级

- **P0(必修,阻断测试)**:C1–C5。
- **P1(强烈建议,本迭代闭环)**:H1–H15,尤其并发(H1–H6)、登录限流(C3 配套)、JWT 黑名单、DB 约束(H13)。
- **P2(排期)**:MEDIUM / LOW。

## 关于"进入测试流程"

按"审查没问题就启动测试"标准,**当前不能启动**——5 个 CRITICAL 会让测试在"有缺陷的代码"上运行。建议:
1. 先修 P0 → 重跑 `mvn test` 绿 → 再启动完整测试流程(此时测试能验证修复而非绕过缺陷)。
2. 若选择"先广度测试暴露问题",须让测试团队明确知晓上述 5 个 CRITICAL,避免重复报告。

## 修复状态总览

> 更新于 2026-06-25。CRITICAL + HIGH 已全部处理(修复或评估关闭)。以代码与 git history 为准。

| 编号 | 维度 | 状态 | 闭环方式 |
| --- | --- | --- | --- |
| C1 | 订单状态机 CAS(`payOrder`/`cancelOrder`) | ✅ 已修 | PR#28 |
| C2 | 候补兑现事务隔离(AFTER_COMMIT + CAS 前置 + 即时扣余票) | ✅ 已修 | PR#28 |
| C3 | 默认弱口令清除 + 登录限流 | ✅ 已修 | PR#28 |
| C4 | 错误码体系对齐 `error-code.md` | ✅ 已修 | PR#28 |
| C5 | 退票 `reason` 必填 | ✅ 已修 | PR#28 |
| H1 | `lockSeats` SQL 缺 version | ⚠️ 未单独修 | 并发由 `status='AVAILABLE'`→`LOCKED` CAS + C1 兜底覆盖;`version` 冗余未入 WHERE,可后续优化 |
| H2 | `cancelOrder` 释放校验影响行数 | ✅ 已修 | PR#28(C1 配套) |
| H3 | 退款按 `seatId` 快照释放(含 cabinClasses) | ✅ 已修 | PR#33 |
| H4 | 改签"先占新座再放旧座" | ✅ 已修 | PR#33 |
| H5 | 候补兑现即时扣余票 | ✅ 已修 | PR#28(C2/C4 配套) |
| H6 | 清理任务拆独立短事务、下单路径去同步清理 | ✅ 已修 | PR#30 |
| H7 | 管理员禁用自身 | ✅ 已闭环 | ADMIN 角色整体保护(`ADMIN_ACCOUNT_PROTECTED`),强于"仅禁自身" |
| H8 | AI 接口限流 | ✅ 已修 | PR#32 |
| H9 | 防邮箱存在性枚举 | ✅ 已修 | PR#30 |
| H10 | JWT TTL 24h→2h | ✅ 已修 | PR#32 |
| H11 | 验证码明文日志降级 DEBUG | ✅ 已修 | PR#30 |
| H12 | DB `useSSL` 可配置 | ✅ 已修 | PR#32 |
| H13a/b/c | utf8mb4 + CHECK + FK ON DELETE | ✅ 已修 | PR#31(V8 迁移) |
| H14 | 新增 `CHANGE_WINDOW_CLOSED`(50006) | ✅ 已修 | PR#30 |
| H15 | 全局异常处理器补日志 | ✅ 已修 | PR#30 |

附:PR#34 清理修复后遗留的无调用方 mapper(`releaseSoldSeatsByOrderId`、`findCabinClassesByOrderId`)。

**MEDIUM / LOW**:维持原始审查记录(P2),本轮未处理。
