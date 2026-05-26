# 07_API_DESIGN：接口设计

## 1. 接口规范

统一前缀：

```text
/api
```

统一响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

幂等约定：

- 创建订单、支付、取消、退票、候补提交、候补支付、候补取消和改签提交都可能被前端重复点击或网络重试；
- 状态变更接口必须根据当前状态返回稳定结果，不能重复扣减余票、重复释放座位或重复生成记录；
- 创建订单接口推荐支持请求头 `Idempotency-Key`，同一用户在短时间内使用相同 key 重复请求时返回同一个订单结果；
- MVP 可以先不实现通用幂等表，但支付、取消、退票、候补支付和候补退款必须通过订单状态校验保证业务幂等。

分页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "size": 10
  }
}
```

## 2. 认证接口

### 发送邮箱验证码

```http
POST /api/auth/email-code
```

请求：

```json
{
  "email": "user@example.com",
  "scene": "REGISTER"
}
```

`scene` 可选值：

```text
REGISTER
LOGIN
RESET_PASSWORD
```

### 邮箱验证码注册

```http
POST /api/auth/register
```

请求：

```json
{
  "email": "user@example.com",
  "code": "123456",
  "password": "User@123456",
  "confirmPassword": "User@123456",
  "nickname": "SkyBooker用户"
}
```

### 用户端邮箱密码登录

```http
POST /api/auth/login
```

请求：

```json
{
  "email": "user@example.com",
  "password": "User@123456"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "jwt-token",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": 1,
      "email": "user@example.com",
      "nickname": "SkyBooker用户",
      "role": "USER"
    }
  }
}
```

该接口只允许 `role = USER` 的普通用户登录。若账号为 `ADMIN`，返回无权限或账号类型不匹配，管理员必须从管理后台入口登录。

管理后台页面入口为前端路由 `/admin`，不属于后端 JSON API。后端管理接口仍统一使用 `/api/admin/**`。

### 管理员用户名密码登录

```http
POST /api/admin/auth/login
```

请求：

```json
{
  "username": "admin",
  "password": "Admin@123456"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "admin-jwt-token",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "admin": {
      "id": 1,
      "userId": 1,
      "username": "admin",
      "realName": "系统管理员",
      "role": "ADMIN"
    }
  }
}
```

后端通过 `admin_user.username` 定位管理员资料，再关联 `users` 校验密码、`users.status`、`admin_user.status` 和 `role = ADMIN`。普通用户即使知道邮箱和密码，也不能通过该接口登录。

### 找回密码

```http
POST /api/auth/reset-password
```

请求：

```json
{
  "email": "user@example.com",
  "code": "123456",
  "newPassword": "NewUser@123456",
  "confirmPassword": "NewUser@123456"
}
```

### 当前用户

```http
GET /api/auth/me
```

仅接受用户端 Token。若 Token 对应 `role = ADMIN` 或 `loginPortal = ADMIN`，返回无权限或账号类型不匹配。

### 退出登录

```http
POST /api/auth/logout
```

仅处理用户端 Token。管理员退出登录使用 `/api/admin/logout`。

### 管理员退出登录

```http
POST /api/admin/logout
```

## 3. 航班接口

### 航班查询

```http
GET /api/flights
```

查询参数：

```text
flightNo
departureCity
arrivalCity
departureDate
airlineId
minPrice
maxPrice
departureTimeStart
departureTimeEnd
maxDurationMinutes
directOnly
status
sort
page
size
```

### 航班详情

```http
GET /api/flights/{id}
```

### 查询座位图

```http
GET /api/flights/{id}/seats
```

## 4. 乘机人接口

```http
GET    /api/passengers
POST   /api/passengers
PUT    /api/passengers/{id}
DELETE /api/passengers/{id}
```

## 5. 订单接口

### 创建订单

```http
POST /api/orders
```

请求示例：

```json
{
  "flightId": 1001,
  "items": [
    {
      "passengerId": 1,
      "seatId": 10001
    }
  ]
}
```

校验规则：

- `items` 不能为空；
- `passengerId` 和 `seatId` 必须一一对应；
- 同一订单内乘机人不能重复；
- 同一订单内座位不能重复；
- 所有乘机人必须属于当前登录用户；
- 所有座位必须属于 `flightId` 对应航班；
- 座位必须为 `AVAILABLE`。

### 模拟支付

```http
POST /api/orders/{id}/pay
```

幂等规则：

- `PENDING_PAYMENT` 且未超时：执行支付，订单变为 `ISSUED`，座位变为 `SOLD`；
- 已经是 `ISSUED`：返回当前订单成功结果；
- 已经是 `CANCELLED`、`REFUNDED` 或已超时：返回订单状态不允许操作。

### 查询我的订单

```http
GET /api/orders
```

### 订单详情

```http
GET /api/orders/{id}
```

### 取消订单

```http
POST /api/orders/{id}/cancel
```

幂等规则：

- `PENDING_PAYMENT`：取消订单并释放座位；
- 已经是 `CANCELLED`：返回当前取消结果；
- `ISSUED` 或其他状态：返回订单状态不允许操作。

## 6. 退票接口

```http
POST /api/orders/{id}/refund
```

请求示例：

```json
{
  "reason": "行程变更"
}
```

幂等规则：

- `ISSUED`：计算手续费、生成退票记录、释放座位并触发候补；
- 已经是 `REFUNDED`：返回现有退票结果；
- 其他状态：返回订单状态不允许操作。

## 7. 改签接口

### 查询可改签航班

```http
GET /api/orders/{id}/change-options
```

### 提交改签

```http
POST /api/orders/{id}/change
```

请求示例：

```json
{
  "newFlightId": 1002,
  "items": [
    {
      "passengerId": 1,
      "newSeatId": 20001
    }
  ]
}
```

改签属于加分版本。实现时必须保证旧座位释放、新座位锁定、差价记录和订单状态更新在同一事务内完成。

## 8. 候补接口

### 提交候补

```http
POST /api/waitlist
```

请求示例：

```json
{
  "flightId": 1001,
  "cabinClass": "ECONOMY",
  "passengerIds": [1]
}
```

后端根据 `passengerIds` 生成 `waitlist_passenger` 明细，并根据 `cabinClass` 和乘机人数计算 `payAmount`。候补只允许在目标航班目标舱位无可售座位时提交。提交成功后生成 `PENDING_PAYMENT` 候补单，不进入候补队列，也不占用座位。

多人候补必须整体满足。候补支付成功后才进入队列，排序使用 `paid_at ASC, id ASC`。候补兑现只能分配同一 `cabinClass` 的座位。

### 我的候补订单

```http
GET /api/waitlist/my
```

### 支付候补订单

```http
POST /api/waitlist/{id}/pay
```

幂等规则：

- `PENDING_PAYMENT` 且未超时：按候补单 `payAmount` 支付成功，候补状态变为 `WAITING`，写入 `paid_at`，进入候补队列；
- 已经是 `WAITING`：返回当前候补排队结果；
- 已经是 `SUCCESS`：返回当前成功结果和正式订单 ID；
- `CANCELLED`、`FAILED`、`REFUNDED`、`EXPIRED`：返回状态不允许操作。

候补兑现由退票、航班取消前的定时任务或后台处理流程触发，不依赖用户再次支付。兑现成功后，系统分配座位、创建正式订单、写入 `waitlist_order.ticket_order_id`，并把候补状态改为 `SUCCESS`。

### 取消候补订单

```http
POST /api/waitlist/{id}/cancel
```

幂等规则：

- `PENDING_PAYMENT`：取消候补单，状态变为 `CANCELLED`，无需退款；
- `WAITING`：取消候补单并发起原路退款，退款成功后状态变为 `REFUNDED`；
- `SUCCESS`：不允许取消候补单，用户需要对正式订单发起退票；
- 已经是 `CANCELLED` 或 `REFUNDED`：返回当前结果。

## 9. AI 智能购票助手接口

AI 智能购票助手属于用户端购票流程，允许匿名访问或 `role = USER` 的用户访问。管理员 Token 不作为用户端身份使用，不能访问 `/api/ai/**`；如后续需要后台 AI 统计，另行设计 `/api/admin/ai/**`。

AI 会话对外使用 `sessionId`，对应数据库中的 `ai_chat_session.public_session_id`，不是数据库自增主键。服务端生成该值时必须使用 UUID、ULID 或同等级不可猜测随机值。

会话归属规则：

- 未登录用户创建匿名会话，`user_id = NULL`，读取和删除会话时通过不可猜测的 `sessionId` 定位；
- 登录普通用户创建会话时写入 `user_id`，读取和删除时必须校验当前 Token 对应用户与会话 `user_id` 一致；
- 管理员 Token 必须被拒绝；
- 前端不得传入数据库内部 `id`。

### 发送消息

```http
POST /api/ai/chat
```

请求：

```json
{
  "sessionId": "optional-public-session-id",
  "message": "我明天从上海去北京，便宜一点"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "01JZ8X9K7M2Q3R4S5T6V7W8XYZ",
    "replyType": "FLIGHT_RECOMMENDATION",
    "replyText": "已为你找到 3 个从上海飞往北京的可购买航班，已按价格从低到高排序。",
    "searchUrl": "/flights?departureCity=上海&arrivalCity=北京&departureDate=2026-05-26&sort=price_asc",
    "flights": [
      {
        "flightId": 1001,
        "flightNo": "MU5101",
        "airlineName": "东方航空",
        "departureCity": "上海",
        "arrivalCity": "北京",
        "departureTime": "08:30",
        "arrivalTime": "10:45",
        "durationMinutes": 135,
        "price": 680,
        "remainingSeats": 24,
        "status": "ON_TIME",
        "detailUrl": "/flights/1001",
        "bookingUrl": "/booking/1001"
      }
    ],
    "quickActions": [
      {
        "label": "查看全部结果",
        "url": "/flights?departureCity=上海&arrivalCity=北京&departureDate=2026-05-26&sort=price_asc"
      }
    ]
  }
}
```

### 获取会话消息

```http
GET /api/ai/sessions/{sessionId}/messages
```

`sessionId` 为公开会话 ID。匿名会话只允许访问 `user_id = NULL` 的会话；登录用户只允许访问属于自己的会话。

### 删除会话

```http
DELETE /api/ai/sessions/{sessionId}
```

删除会话时使用同样的归属规则，不能通过猜测他人的 `sessionId` 删除他人会话。

## 10. 管理后台接口

管理后台业务接口统一要求登录用户具备 `ADMIN` 角色。`/admin` 是前端页面路由，不由后端 API 返回 JSON；`POST /api/admin/auth/login` 登录接口公开访问。其余后台接口从 JWT 对应的管理员 Principal 中读取 `userId`、`email`、`role` 和 `loginPortal`，不接收前端传入的管理员身份字段。

```text
Authorization: Bearer <admin-token>
```

当前管理员：

```http
GET /api/admin/me
```

用于校验当前 Token 是否具备后台访问权限，并返回当前管理员资料。

### 航班管理

```http
GET    /api/admin/flights
POST   /api/admin/flights
PUT    /api/admin/flights/{id}
POST   /api/admin/flights/{id}/publish
POST   /api/admin/flights/{id}/unpublish
POST   /api/admin/flights/{id}/generate-seats
```

### 订单管理

```http
GET /api/admin/orders
GET /api/admin/orders/{id}
```

### 普通用户管理

```http
GET  /api/admin/users
POST /api/admin/users/{id}/disable
POST /api/admin/users/{id}/enable
```

普通用户管理接口只面向普通用户账号。`GET /api/admin/users` 默认只返回 `role = USER` 的普通用户；后台不能通过该接口禁用 `role = ADMIN` 的管理员账号，也不能禁用当前登录管理员自身。管理员启停应通过单独的管理员管理能力扩展。

### 数据统计

```http
GET /api/admin/dashboard/summary
GET /api/admin/dashboard/hot-routes
GET /api/admin/dashboard/order-status
```
