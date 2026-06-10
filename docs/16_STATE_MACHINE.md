# 16_STATE_MACHINE：核心状态机

本文档用于统一订单、座位、候补和退改签状态，后续实现和测试以这里为准。

## 1. 订单状态

```text
PENDING_PAYMENT 待支付
ISSUED          已出票
CANCELLED       已取消
REFUNDED        已退票
CHANGE_PENDING  改签处理中
CHANGED         已改签
```

基础版本普通订单支付成功后直接进入 `ISSUED`。候补兑现成功时创建的正式订单也直接为 `ISSUED`。`PAID` 不作为基础状态，真实支付或异步出票扩展时再引入。

状态流转：

```text
PENDING_PAYMENT -> ISSUED
PENDING_PAYMENT -> CANCELLED
ISSUED -> REFUNDED
ISSUED -> CHANGED
```

约束：

- 票务订单的 `PENDING_PAYMENT` 只能由创建订单产生；
- `ISSUED` 由普通订单模拟支付成功或候补兑现出票产生；
- `CANCELLED` 只能取消待支付订单；
- `REFUNDED` 只能由已出票订单退票产生；
- 当前加分版本改签采用单步确认，成功后直接 `ISSUED -> CHANGED`；
- `CHANGE_PENDING` 预留给后续真实支付差价、人工审核或异步出票流程，当前单步改签实现不得产生该状态；
- 所有状态变更接口必须具备幂等语义，重复请求不能重复扣减或释放座位。

## 2. 座位状态

```text
AVAILABLE 可选
LOCKED    锁定中
SOLD      已售
DISABLED  不可选
```

状态流转：

```text
AVAILABLE -> LOCKED -> SOLD
LOCKED -> AVAILABLE
SOLD -> AVAILABLE
AVAILABLE -> DISABLED
DISABLED -> AVAILABLE
```

锁座时必须写入 `locked_by_order_id` 和 `lock_expire_time`。订单取消、超时、退票和改签释放座位时必须清空锁定字段。

约束：

- `AVAILABLE -> LOCKED` 必须使用条件更新，条件至少包含 `id`、`status = 'AVAILABLE'` 和 `version`；
- `LOCKED -> SOLD` 必须校验 `locked_by_order_id` 属于当前订单；
- 释放座位时只允许释放当前订单锁定或已售出的座位；改签释放旧座位时必须按改签前旧 `seat_id` 白名单更新，不能在新座位写入同一订单 ID 后按 `order_id` 全量释放；
- `DISABLED` 座位不能参与订单、候补兑现和改签锁座。

## 3. 候补状态

```text
PENDING_PAYMENT 待支付
WAITING         已支付，排队中
SUCCESS         候补成功，已出票
CANCELLED       用户已取消
FAILED          候补失败
REFUNDED        已退款
EXPIRED         支付超时
```

状态流转：

```text
PENDING_PAYMENT -> WAITING
PENDING_PAYMENT -> EXPIRED
PENDING_PAYMENT -> CANCELLED
WAITING -> SUCCESS
WAITING -> CANCELLED -> REFUNDED
WAITING -> FAILED
FAILED -> REFUNDED
```

候补在支付成功后才进入队列，按 `paid_at ASC, id ASC` 先来先服务。候补兑现只能分配候补单目标舱位内的座位，兑现成功后座位写入 `waitlist_passenger.locked_seat_id` 和 `locked_seat_no`。

多人候补必须整体满足，不允许部分乘机人成功、部分乘机人继续等待。`paid_at` 相同时使用 `id` 排序，避免同一时间支付时排序不稳定。

`WAITING -> SUCCESS` 时必须生成正式订单，并把订单 ID 写入 `waitlist_order.ticket_order_id`。重复触发已成功候补时，应返回同一个正式订单。

已支付候补取消后才进入 `REFUNDED`；`PENDING_PAYMENT -> CANCELLED` 不需要退款。

## 4. 余票一致性

`flight.remaining_seats` 是可售余票缓存，必须与 `flight_seat` 保持一致：

- 锁座成功：`remaining_seats - passenger_count`；
- 订单超时或取消：释放座位并 `remaining_seats + passenger_count`；
- 支付出票：座位 `LOCKED -> SOLD`，余票不再变化；
- 退票：座位释放，若没有候补兑现则 `remaining_seats + passenger_count`；
- 退票后候补兑现：座位直接更新为 `SOLD` 并生成正式订单，余票不增加；
- 改签：旧航班旧座位释放、新航班新座位直接售出，两个航班分别维护余票。

实现约束：

- 余票更新必须与座位状态更新处于同一数据库事务；
- 订单支付时座位从 `LOCKED` 变为 `SOLD`，不改变余票，因为锁座阶段已经扣减；
- 任何失败回滚后，`remaining_seats` 与 `flight_seat.status = 'AVAILABLE'` 聚合结果必须一致；
- 建议提供一个后台或测试用校验 SQL，定期比对 `flight.remaining_seats` 与可售座位数量。
