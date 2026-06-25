# 并发与事务审查报告

> **📌 状态(2026-06-25):本报告所列 CRITICAL + HIGH 已全部处理**(修复或评估关闭)。统一追踪与逐项 PR 映射见 [汇总报告 · 修复状态总览](2026-06-24-backend-deep-review.md#修复状态总览)。以下为审查时点(2026-06-24)的原始记录,保留作回溯;MEDIUM/LOW 维持 P2 排期未处理。

- **维度**:座位库存并发、订单状态机、退款/改签/候补事务边界、分布式锁、调度任务
- **审查方**:java-reviewer
- **日期**:2026-06-24

依据文档:`docs/16_STATE_MACHINE.md`、`docs/10_BACKEND_DESIGN.md`、`docs/01_REQUIREMENTS.md`、`docs/02_FEATURE_SPEC.md`。

## CRITICAL

### C1 — `OrderService.payOrder` / `cancelOrder` 状态流转未用 CAS,可并发重复推进
- **位置**:`order/service/OrderService.java:127, 164`;`OrderMapper.xml:99`
- **证据**:`updateOrderStatus` SQL 是无条件 `UPDATE ticket_order SET status=? WHERE id=?`,仅依赖前置内存判断。
- **风险**:
  - `payOrder` 与 `cancelOrder` 并发:两线程都读 `PENDING_PAYMENT`;若取消先成功释放座位 + `+remaining_seats`,支付后 `updateSeatStatusToSold`(条件 LOCKED)影响 0 行,但 `cancelOrder` 的 `releaseSeatsByOrderId`(:165-166)**不校验 release 影响行数**直接 `incrementRemainingSeats` → 余票凭空 +N → 超卖。
  - 两个并发 `cancelOrder` 都进入 → 重复 `incrementRemainingSeats` → 余票翻倍。
- **修复**:`payOrder`/`cancelOrder` 用 `updateOrderStatusCAS(id, expected, new)`,影响 0 即幂等返回;`cancelOrder` 释放座位后校验影响行数 == passengerCount。

### C2 — 退款/改签后触发候补兑现,事务嵌套导致主事务被污染
- **位置**:`refund/service/RefundService.java:79` → `waitlist/service/WaitlistFulfillmentService.java`
- **证据**:`refundOrder`(`@Transactional`)调 `tryFulfillWaitlists`(`@Transactional` REQUIRED,同事务);后者内部抛 `RuntimeException("Seat lock mismatch ...")`(:89)/`"state changed"`(:118)会标记外部退款事务 rollback-only。
- **风险**:退款主流程已成功(CAS、退款记录、座位释放、余票增加),仅因候补兑现里某个无关候补竞争失败就把退款整体回滚 → 用户退款失败(实际是候补的锅)。
- **修复**:候补兑现移到退款事务 `@TransactionalEventListener(AFTER_COMMIT)`,或 `Propagation.REQUIRES_NEW` 隔离并吞异常,绝不影响主退款事务。

### C3 — 候补兑现 CAS 顺序倒置:写订单/锁座之后才抢态
- **位置**:`waitlist/service/WaitlistFulfillmentService.java:83-119`
- **证据**:流程 `insertOrder`(ISSUED)→ 锁座 → 写 order_passenger → `updatePassengerSeat` → `updateTicketOrderId` → **最后** `updateStatusCAS(WAITING→SUCCESS)`(:116)。
- **风险**:正常路径 CAS 放最后,前面所有写操作在"乐观假设"下进行;CAS 失败(`cas==0`)抛异常依赖事务回滚,但与 C2 叠加后污染退款事务。
- **修复**:`WAITING→SUCCESS` 的 CAS 必须在创建正式订单/锁座**之前**(先抢态再做事)。

### C4 — 候补兑现与正常下单之间存在座位超卖窗口
- **位置**:`WaitlistFulfillmentService.java:49-57, 124-126`;`FlightMapper.xml:280`
- **证据**:候补兑现循环 `countAvailableSeatsByFlightAndCabin` → `lockAvailableSeatsForWaitlist` → 循环下一条 → **最后统一** `decrementRemainingSeats`(只扣一次 `consumedByFulfillment`)。
- **风险**:扣减前正常 `OrderService.createOrder` 的 `decrementRemainingSeats` 条件 `remaining_seats >= count` 看到的是未扣减旧余票,通过校验锁座 → 最终 `remaining_seats` 被扣到负数或与 SOLD 座位数对不上(违反 `16_STATE_MACHINE.md:106, 113`)。
- **修复**:候补兑现每锁一组座位立即在同事务内 `decrementRemainingSeats`(粒度到单条候补),或兑现前先条件扣总余票做"预占"。

## HIGH

### H1 — `createOrder` 先建订单后锁座,`lockSeats` 缺 version 条件
- **位置**:`OrderService.java:72-83`;`FlightMapper.xml` lockSeats
- **证据**:`insertOrder` → `lockSeats`(条件仅 `status='AVAILABLE'`,无 `version = #{version}`)→ `decrementRemainingSeats`。
- **风险**:丢失文档要求的 version 乐观锁语义(`10_BACKEND_DESIGN.md:189`),version 字段未参与 WHERE,等同摆设。
- **修复**:`lockSeats` SQL 加 `AND version = #{version}`(需先读 version)。

### H2 — `cancelOrder` 释放座位未校验 release 影响行数
- **位置**:`OrderService.java:165-166`
- **证据**:`releaseSeatsByOrderId`(条件 `status='LOCKED' AND locked_by_order_id=orderId`)返回 int 被忽略,无条件 `incrementRemainingSeats`。
- **风险**:座位已被超时清理(`OrderCleanupService.cancelSingleExpiredOrder` CAS 抢先)或其他路径释放过时,余票凭空增加。与 C1 叠加。
- **修复**:校验 `release == passengerCount`,或与 CAS 强绑定(仅抢到状态的事务执行 release + increment)。

### H3 — 退款释放座位按 orderId 全量,与改签后场景冲突
- **位置**:`RefundService.java:75`;`FlightMapper.xml:353`
- **证据**:`releaseSoldSeatsByOrderId` 按 `locked_by_order_id=orderId AND status='SOLD'` 全量释放。改签时 `sellAvailableSeatsByIds(newSeatIds, orderId)` 把新座位 orderId 写为同一订单(`FlightMapper.xml:401`)。
- **风险**:改签后若 orderId 名下同时存在旧+新 SOLD(异常/并发改签),全量 release 误释放(违反 `16_STATE_MACHINE.md:63`)。
- **修复**:退款释放基于 `order_passenger.seat_id` 快照(按 seatId 白名单),而非 orderId 全量。

### H4 — `changeOrder` 释放旧座与占新座非原子,跨航班竞态
- **位置**:`ChangeService.java:131-146`
- **证据**:`releaseSoldSeatsBySeatIds`(旧座→AVAILABLE)→ ... → `sellAvailableSeatsByIds`(新座→SOLD);`incrementRemainingSeats`(旧航班)在 `decrementRemainingSeats`(新航班)之前。
- **风险**:release 与 sell 之间无跨航班原子保证;新座被并发抢走时抛错回滚(行为正确但体验差)。
- **修复**:确保订单元数据与新座位一致后再释放旧座;文档化"先占新再放旧"顺序。

### H5 — 候补兑现循环内逐条查询,余票计数基准漂移
- **位置**:`WaitlistFulfillmentService.java:42-122`
- **证据**:每条候补重新 `countAvailableSeatsByFlightAndCabin`(:49)、`findAvailableSeatsByFlightAndCabin`(:56);`consumedByFulfillment` 循环末尾才扣减。
- **风险**:事务未提交期间 `remaining_seats` 与实际 SOLD 不一致(违反 `16_STATE_MACHINE.md:113`);外部下单预检查读到旧余票,产生无效下单尝试。
- **修复**:每锁一组立即扣余票。

### H6 — `@Scheduled` 清理任务整批大事务 + 下单同步触发全量清理
- **位置**:`WaitlistCleanupScheduler.java:15, 24`;`OrderService.java:47`
- **证据**:`cleanupExpiredPending`/`cleanupUnfulfillableWaiting` 整批包一个大事务;`createOrder` 在 :47 同步调用 `cleanupService.cleanupAllExpiredOrders()`。
- **风险**:每次下单触发全表过期清理(大事务),严重影响下单延迟与 DB 负载;默认单线程调度器,长事务阻塞所有 `@Scheduled`。
- **修复**:下单路径不同步触发清理,改纯后台调度;拆分单条清理为独立短事务;配置 `TaskScheduler` 线程池。

## MEDIUM

- **M1** 退款/改签窗口用 `Duration.compareTo(Duration.ofHours(24))`,`LocalDateTime.now()` 依赖 JVM 默认时区,部署时区与业务不一致时窗口偏移 8 小时 — `RefundService.java:87,94` / `ChangeService.java:187,243`。改用 `ZoneId.of("Asia/Shanghai")`。
- **M2** `findExpiredPending` 用 DB `NOW()`,创建时用应用 `LocalDateTime.now()`,时钟漂移导致刚创建被判过期或永不过期 — `WaitlistMapper.xml:133`。统一以 DB 时间为准。
- **M3** `cancelSingleExpiredOrder` 用 `findDetailById`(带乘客联表)仅取 passengerCount,放大事务时长 — `OrderCleanupService.java:53-55`。改轻量 `countPassengersByOrderId`。
- **M4** 订单/候补号 `new Random()` + 秒级时间戳,高并发撞号 — `OrderService.java:233` 等。改 `ThreadLocalRandom` 或 Snowflake。
- **M5** `payWaitlist` CAS 成功后才写 paidAt,paidAt 是排序键,崩溃可致 NULL 排最前,破坏先来先服务 — `WaitlistService.java:130-134`。CAS 同 SQL 写 paid_at。
- **M6** 无分布式锁,多实例 `@Scheduled` 重复执行(功能靠 CAS 兜底正确,但查询重复)— `WaitlistCleanupScheduler`。多实例用 ShedLock。

## LOW

- **L1** `releaseSeatsByOrderId`/`releaseSoldSeatsByOrderId` 正确清空 `locked_by_order_id`,未发现问题。
- **L2** `validateSeats` 仅校验 AVAILABLE,与 lockSeats CAS 重复但非原子(TOCTOU 误导);可删预检查对 status 的强依赖。

## 未发现

- **SQL 注入**:所有 Mapper 用 `#{}`,未发现 `${}` 拼接。
- **字段注入**:`@RequiredArgsConstructor` + final 字段,构造器注入正确。
- **业务逻辑泄漏到 Controller**:Service 承载全部逻辑。
- **`@Transactional` 自调用失效**:RefundService 调 WaitlistFulfillmentService 是跨 Bean(代理生效,但带来 C2 传播问题)。
- **PII/Token 日志**:未发现敏感日志。

## 并发安全总体评估

**不通过(Block)**。4 个 CRITICAL + 6 个 HIGH。核心病灶:
1. **订单状态机原子性缺失**:`payOrder`/`cancelOrder` 仍用无条件 update,而 `refundOrder`/`changeOrder` 已用 CAS —— 实现不一致,前者必须补齐。
2. **候补兑现的事务与顺序设计错误**:CAS 放末尾、余票延迟统一扣减、被退款事务包裹且异常污染主事务。这是系统最脆弱链路。
3. **座位释放策略混用**:退款按 orderId 全量、改签按 seatId 白名单,组合场景冲突,违反状态机文档明确警告。

单实例低并发可"看起来工作",生产并发/多实例下,座位超卖、余票漂移、重复退款、退款 500 会集中爆发。建议合并前优先修复 C1–C4,并补并发集成测试(同座位双下单、支付/取消竞态、退款+候补兑现竞态、改签+下单库存竞态)。
