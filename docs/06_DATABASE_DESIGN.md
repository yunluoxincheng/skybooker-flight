# 06_DATABASE_DESIGN：数据库设计

## 1. 设计原则

数据库设计围绕航班、座位、订单、退改签、候补和 AI 聊天记录展开。

核心原则：

- 航班与座位分离；
- 订单与乘机人分离；
- 座位状态可追踪；
- 订单状态明确；
- 退票、改签、候补均保留记录；
- AI 推荐结果可追溯；
- 使用 Flyway 进行版本化迁移。

## 2. 核心表列表

| 表名 | 说明 |
|---|---|
| users | 用户表 |
| passenger | 乘机人表 |
| admin_user | 管理员用户名和扩展资料表，不保存密码哈希 |
| airline | 航空公司表 |
| airport | 机场表 |
| flight | 航班表 |
| flight_seat | 航班座位表 |
| ticket_order | 订单表 |
| order_passenger | 订单乘客表 |
| refund_record | 退票记录表 |
| change_record | 改签记录表 |
| waitlist_order | 候补订单表 |
| waitlist_passenger | 候补乘机人明细表 |
| ai_chat_session | AI 会话表 |
| ai_chat_message | AI 消息表 |
| ai_recommendation_record | AI 推荐记录表 |

## 2.1 约束策略

数据库负责保证强身份关系和基础引用完整性，例如：

- 乘机人必须属于存在的用户；
- 航班必须关联存在的航司和机场；
- 座位必须属于存在的航班；
- 订单必须关联存在的用户和航班；
- 订单乘机人明细必须关联存在的订单、乘机人和座位；
- 候补明细必须关联存在的候补订单。

业务条件由 Service 层保证，例如：

- `admin_user.user_id` 对应的 `users.role` 必须是 `ADMIN`；
- 普通用户只能操作自己的乘机人、订单和候补；
- 创建订单时，乘机人和座位必须一一对应；
- 座位必须属于订单指定航班；
- 候补只能在目标航班无可售座位时提交；
- 余票缓存 `remaining_seats` 必须与座位状态在同一事务中维护。

如果后续为了课堂演示或重置数据需要弱化外键，必须在脚本和文档中明确说明，并在 Service 层补足校验。

## 3. 航班表 flight

用于记录某一天的具体航班。

关键字段：

- 航班号；
- 航空公司；
- 出发机场；
- 到达机场；
- 起飞时间；
- 到达时间；
- 飞行时长；
- 基础票价；
- 航班状态；
- 历史准点率；
- 是否直飞；
- 行李额；
- 上下架状态。

`remaining_seats` 表示可售余票缓存，必须与 `flight_seat` 状态保持一致。创建订单锁座、支付出票、取消订单、退票、候补锁座和改签都必须在同一事务中同步维护余票；查询展示可以优先使用该字段，定期校验时可按 `flight_seat.status = 'AVAILABLE'` 聚合核对。

## 4. 座位表 flight_seat

用于记录某个航班下的具体座位状态。

座位状态：

```text
AVAILABLE 可选
LOCKED    锁定中
SOLD      已售
DISABLED  不可选
```

关键字段：

- flight_id；
- seat_no；
- cabin_class；
- seat_type；
- price；
- status；
- version；
- locked_by_order_id；
- lock_expire_time。

`version` 字段用于乐观锁并发控制。

## 5. 订单表 ticket_order

订单状态：

```text
PENDING_PAYMENT 待支付
ISSUED          已出票
CANCELLED       已取消
REFUNDED        已退票
CHANGE_PENDING  改签处理中
CHANGED         已改签
```

基础版本建议支付成功后直接进入 `ISSUED`，不单独保留 `PAID`。如果后续接入真实支付和异步出票，再新增 `PAID` 作为中间状态。

订单金额包括：

- 票价；
- 机场建设费；
- 燃油费；
- 手续费；
- 总价。

## 6. 候补订单表 waitlist_order

候补状态：

```text
WAITING      排队中
WAITING_PAY  已锁座，待支付
SUCCESS      候补成功
CANCELLED    已取消
EXPIRED      已过期
```

候补按创建时间排序，先提交的用户优先获得退票释放出的座位。

候补乘机人和候补锁定座位保存在 `waitlist_passenger` 表中，避免多人候补时只记录人数而丢失乘机人身份。候补成功锁座后，每个候补乘机人明细记录对应的 `locked_seat_id` 和 `locked_seat_no`。

`waitlist_order.ticket_order_id` 用于记录候补支付成功后生成的正式订单 ID。

`waitlist_order.last_skip_reason` 用于记录多人候补因本次释放座位数不足而被临时跳过的原因，便于后台排查。该字段只用于说明最近一次候补分配尝试，不参与排序。

写入规则：

- `WAITING` 阶段为空；
- `WAITING_PAY` 阶段仍为空，只在 `waitlist_passenger` 中记录锁定座位；
- 候补支付成功后创建 `ticket_order` 和 `order_passenger`，再把 `ticket_order_id` 写回 `waitlist_order`；
- `SUCCESS` 状态下必须存在 `ticket_order_id`。

## 7. AI 表设计

### ai_chat_session

保存 AI 聊天会话。`public_session_id` 是返回给前端的公开会话 ID，必须使用 UUID、ULID 或同等级不可猜测随机值；数据库自增 `id` 只作为内部主键，不暴露给前端。`user_id` 可以为空，表示匿名会话。

### ai_chat_message

保存用户和助手的消息。

`extra_json` 用于保存推荐航班卡片、快捷按钮和跳转链接。

### ai_recommendation_record

保存一次 AI 推荐的解析条件和推荐航班 ID，便于后续统计。

## 8. ER 关系概览

```text
users 1 ---- n passenger
users 1 ---- n ticket_order
flight 1 ---- n flight_seat
flight 1 ---- n ticket_order
ticket_order 1 ---- n order_passenger
ticket_order 1 ---- 0..1 refund_record
ticket_order 1 ---- 0..n change_record
flight 1 ---- n waitlist_order
ticket_order 0..1 ---- 0..1 waitlist_order
waitlist_order 1 ---- n waitlist_passenger
users 0..1 ---- n ai_chat_session
ai_chat_session 1 ---- n ai_chat_message
ai_chat_session 1 ---- n ai_recommendation_record
```

## 9. 索引建议

航班查询索引：

```sql
CREATE INDEX idx_flight_route_date ON flight(departure_airport_id, arrival_airport_id, departure_time);
CREATE INDEX idx_flight_no_date ON flight(flight_no, departure_time);
```

座位查询索引：

```sql
CREATE INDEX idx_seat_flight_status ON flight_seat(flight_id, status);
```

订单查询索引：

```sql
CREATE INDEX idx_order_user_created ON ticket_order(user_id, created_at);
CREATE INDEX idx_order_no ON ticket_order(order_no);
```

候补查询索引：

```sql
CREATE INDEX idx_waitlist_flight_status_created ON waitlist_order(flight_id, status, created_at);
```

约束索引：

- 所有外键列必须具备索引；
- `order_no` 和 `waitlist_no` 必须唯一；
- `flight_seat(flight_id, seat_no)` 必须唯一；
- 如实现 `Idempotency-Key`，应增加 `(user_id, idempotency_key)` 唯一索引或独立幂等记录表。

## 用户认证相关表设计

### users 用户表

`users` 表用于存储所有登录账号信息，包括普通用户和管理员，认证主标识为邮箱。

关键字段：

- `email`：用户邮箱，唯一；
- `phone`：手机号，可选，作为后续短信登录扩展；
- `password_hash`：BCrypt 密码哈希；
- `nickname`：用户昵称；
- `role`：用户角色，默认为 `USER`；
- `status`：账号状态，如 `NORMAL`、`DISABLED`；
- `email_verified`：邮箱是否已验证；
- `phone_verified`：手机号是否已验证；
- `last_login_at`：最近登录时间。

管理员账号同样存储在 `users` 表中，使用 `role = ADMIN` 区分，不使用独立管理员密码表。用户端登录只允许 `role = USER`，管理端登录只允许 `role = ADMIN`。

### admin_user 管理员扩展资料表

`admin_user` 表保存管理员用户名和扩展资料，但不保存密码哈希。管理端登录时通过 `admin_user.username` 定位管理员，再关联 `users.password_hash` 校验密码。

关键字段：

- `user_id`：通过外键关联 `users.id`，对应 `role = ADMIN` 的用户；
- `username`：管理员登录用户名，唯一；
- `job_no`：管理员工号，可选且唯一；
- `real_name`：管理员真实姓名；
- `status`：后台资料状态，例如 `ENABLED`、`DISABLED`；
- `remark`：备注。

数据库外键只能保证 `admin_user.user_id` 指向存在的 `users.id`；`users.role = ADMIN`、`users.status` 和 `admin_user.status` 必须由管理端登录逻辑和管理员账号维护 Service 层共同校验。

### auth_verification_code_log 验证码发送日志表

验证码实际存储在 Redis 中，该表只记录发送日志，便于排查和防刷统计。

关键字段：

- `target`：邮箱或手机号；
- `target_type`：`EMAIL` 或 `PHONE`；
- `scene`：`REGISTER`、`LOGIN`、`RESET_PASSWORD`；
- `send_status`：发送状态；
- `ip_address`：请求 IP。

### oauth_account 第三方登录扩展表

第三方登录不作为实训主流程，但系统预留扩展表，后续可接入微信、支付宝、GitHub 等身份提供商。

关键字段：

- `user_id`：绑定的本地用户；
- `provider`：第三方平台；
- `open_id`：平台用户唯一标识；
- `union_id`：平台联合标识，可选；
- `nickname`、`avatar_url`：第三方资料快照。
