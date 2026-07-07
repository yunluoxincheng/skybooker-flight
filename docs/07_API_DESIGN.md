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
  "password": "SkyBooker@Init2026!"
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
cabinClass
passengerCount
sort
page
size
```

`cabinClass`/`passengerCount` 用于按舱位过滤座位可用性;传入 `cabinClass` 时,`minPrice`/`maxPrice` 与 `sort=PRICE_ASC` 基于 `flight_cabin.price`(该舱位票价)筛选与排序,否则基于 `flight.base_price`。

### 航班详情

```http
GET /api/flights/{id}
```

返回 `FlightVO`，其中 `cabins` 为该航班各舱位配置列表 `[{cabinClass, price, totalSeats, availableSeats}]`，`availableSeats` 为按舱位实时聚合的可选座位数；未配置舱位的航班为空列表，前端 booking 页据此展示多舱位选择。

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
- `CHANGED`：按改签后的当前航班、当前座位和当前订单金额计算退票，释放改签后的当前座位并触发当前航班候补；
- 已经是 `REFUNDED`：返回现有退票结果；
- 其他状态：返回订单状态不允许操作。

## 7. 改签接口

### 查询可改签航班

```http
GET /api/orders/{id}/change-options
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "flightId": 1002,
      "flightNo": "SB2002",
      "departureTime": "2026-07-01T09:00:00",
      "arrivalTime": "2026-07-01T11:30:00",
      "basePrice": 780.00,
      "remainingSeats": 8,
      "status": "ON_TIME"
    }
  ]
}
```

规则：

- 仅普通用户可查询自己的 `ISSUED` 订单；
- 当前版本采用单步改签，候选航班必须与原订单航班具有相同出发机场和到达机场；
- 候选航班必须已发布、未起飞、状态为 `ON_TIME` 或 `DELAYED`，且可用座位数能满足订单全部乘机人；
- 候选列表必须排除当前订单航班，当前版本不支持同航班仅换座。

### 提交改签

```http
POST /api/orders/{id}/change
```

请求示例：

```json
{
  "newFlightId": 1002,
  "seatMappings": [
    {
      "passengerId": 1,
      "newSeatId": 20001
    }
  ]
}
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 9001,
    "orderNo": "T202607010001",
    "status": "CHANGED",
    "flightId": 1002,
    "totalAmount": 860.00,
    "passengers": [
      {
        "passengerId": 1,
        "passengerName": "张三",
        "passengerType": "ADULT",
        "seatId": 20001,
        "seatNo": "12A",
        "ticketPrice": 780.00
      }
    ]
  }
}
```

确认规则：

- 仅 `ISSUED` 订单允许改签，成功后直接变为 `CHANGED`；
- 当前版本跳过 `CHANGE_PENDING`，该状态仅预留给后续真实差价支付、人工审核或异步出票流程；
- `newFlightId` 必须不同于当前订单航班，并且必须与原航班具有相同出发机场和到达机场；
- `seatMappings` 必须为订单内每个乘机人各提供一个新座位，不能遗漏乘机人、重复乘机人或重复座位；
- 新座位必须属于 `newFlightId`，且状态为 `AVAILABLE`；
- 旧座位释放必须按改签前旧 `seatId` 白名单更新，不能在新座位写入同一订单 ID 后按 `orderId` 全量释放；
- 改签确认必须在同一事务中完成旧座位释放、新座位售出、订单状态更新、订单乘机人座位快照更新、订单金额更新和 `change_record` 写入；
- 任一环节失败必须整体回滚，重复提交在订单已离开 `ISSUED` 后返回当前状态或订单状态错误，但不能重复释放座位、售出座位、扣减余票或插入改签记录。

金额口径：

- `change_record.price_diff = 新座位票价 - 原乘机人票价`，按乘机人记录；
- `change_record.change_fee` 使用当前简化规则记录改签手续费；
- `ticket_order.ticket_amount` 更新为新座位票价合计；
- `ticket_order.airport_fee`、`fuel_fee`、`service_fee` 按既有固定公式和不变乘机人数重新计算；
- `ticket_order.total_amount = ticket_amount + airport_fee + fuel_fee + service_fee`；
- 当前版本不实现真实差价支付或退款，`change_fee` 不计入 `ticket_order.total_amount`，仅作为改签记录审计字段。

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

AI 智能购票助手属于用户端购票流程，允许匿名访问或 `role = USER` 的用户访问。管理员 Token 不作为用户端身份使用，不能访问 `/api/ai/**`。`/api/admin/ai/**` 前缀收敛为 ADMIN portal，目前承载 LLM 运行时配置管理接口（见下文 [§10 管理后台接口](#10-管理后台接口) 的"AI LLM 配置管理"）；后台 AI 统计接口如需要仍另行设计。

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
    "intent": "FLIGHT_QUERY",
    "replyText": "已为你找到 3 个从上海飞往北京的可购买航班，已按价格从低到高排序。",
    "parsedCondition": {
      "departureCity": "上海",
      "arrivalCity": "北京",
      "departureDate": "2026-05-26",
      "sort": "PRICE_ASC"
    },
    "missingFields": [],
    "followUpQuestion": null,
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
        "value": "查看全部结果"
      }
    ]
  }
}
```

`replyType` 取值：

- 搜索链路：`FOLLOW_UP`、`FLIGHT_RECOMMENDATION`、`NO_RESULT`；
- 非搜索会话：`TRAVEL_CHAT`、`BOOKING_HELP`、`OUT_OF_SCOPE`。

`intent` 取值：`TRAVEL_CHAT`、`FLIGHT_QUERY`、`FLIGHT_QUERY_CONTINUATION`、`BOOKING_HELP`、`OUT_OF_SCOPE`。

非搜索会话回复仍返回稳定字段：`parsedCondition` 为空对象、`missingFields` 为空数组、`followUpQuestion` 为 `null`、`searchUrl` 为 `null`、`flights` 为空数组。快捷操作保持文本建议契约，每项只包含 `label` 和 `value`；页面跳转继续使用 `searchUrl`、航班卡片 `detailUrl` 或 `bookingUrl`。

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

### 航司与机场管理

```http
GET    /api/admin/airlines                      # 列表/搜索：?keyword&status&page&size
POST   /api/admin/airlines                      # 新增：code / name / logoUrl
PUT    /api/admin/airlines/{id}                 # 编辑：name / logoUrl（code 创建后不可改）
POST   /api/admin/airlines/{id}/disable         # 禁用
POST   /api/admin/airlines/{id}/enable          # 启用
GET    /api/admin/airports                      # 列表/搜索：?keyword&status&page&size（keyword 命中 code/name/city）
POST   /api/admin/airports                      # 新增：code / name / city / province
PUT    /api/admin/airports/{id}                 # 编辑：name / city / province（code 创建后不可改）
POST   /api/admin/airports/{id}/disable         # 禁用
POST   /api/admin/airports/{id}/enable          # 启用
```

航司与机场是航班的基础资料，采用软状态（`status = ENABLED | DISABLED`），不提供物理删除——二者被 `flight` 外键引用，物理删除将破坏历史航班数据。新增/编辑航班表单以 `GET /api/admin/airlines?status=ENABLED` 与 `GET /api/admin/airports?status=ENABLED` 拉取候选项。`code` 为稳定标识，创建后不可修改；重复 `code` 新增分别返回 `40010 / 40011`。

### 航班管理

```http
GET    /api/admin/flights
POST   /api/admin/flights
PUT    /api/admin/flights/{id}
POST   /api/admin/flights/{id}/publish
POST   /api/admin/flights/{id}/unpublish
GET    /api/admin/flights/{id}/cabins      # 查询舱位配置(各舱 price/totalSeats/availableSeats)
PUT    /api/admin/flights/{id}/cabins      # 设置各舱位价格与座位数(仅未生成座位时；校验 sum=totalSeats、ECONOMY≥BUSINESS≥FIRST)
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

### AI LLM 配置管理

```http
GET /api/admin/ai/llm-config
PUT /api/admin/ai/llm-config
```

管理员在运行时查看（脱敏）和修改 AI 助手的 LLM provider 配置，写入后下一个 AI 请求即生效，无需重启后端。`/api/admin/ai/**` 由 `/api/admin/**` 通配规则收敛为仅 ADMIN portal 可访问。配置优先级：数据库 `ai_llm_config` 记录 > 环境变量 `AI_LLM_*` fallback；apiKey 以 AES-GCM 加密入库（密钥为环境变量 `AI_CONFIG_ENC_KEY`），响应中始终脱敏，形如 `sk****wxyz`。

`GET` 响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "enabled": true,
    "baseUrl": "https://api.openai.com/v1",
    "apiKey": "sk****wxyz",
    "model": "gpt-4o-mini",
    "timeoutMs": 8000,
    "maxRetries": 1,
    "source": "db",
    "updatedBy": 1,
    "updatedAt": "2026-06-26T12:00:00"
  }
}
```

`source` 标识配置来源：`db`（后台管理记录）或 `env-default`（环境变量 fallback）。`updatedBy`/`updatedAt` 反映数据库 `ai_llm_config` 记录的最近修改信息：仅当数据库无该记录时为空；若记录存在但因 `AI_CONFIG_ENC_KEY` 缺失/非法或解密失败而 fallback 到环境变量（`source=env-default`），仍会返回该记录的修改信息。

`PUT` 请求：

```json
{
  "enabled": true,
  "baseUrl": "https://api.openai.com/v1",
  "apiKey": "sk-new-key",
  "model": "gpt-4o-mini",
  "timeoutMs": 8000,
  "maxRetries": 1
}
```

`apiKey` 可选：省略或传 `null` = 保留现有密钥；传非空 = 覆写；传纯空白 = 校验失败。启用（`enabled = true`）时 `baseUrl`、`model`、可用 apiKey 不可为空；`timeoutMs` 必须 > 0、`maxRetries` 必须 >= 0，否则返回 `AI_LLM_CONFIG_INVALID`（10022，HTTP 400）。响应与 `GET` 一致（脱敏）。

### 数据统计

```http
GET /api/admin/dashboard/summary
GET /api/admin/dashboard/hot-routes
GET /api/admin/dashboard/order-status
```

`/api/admin/dashboard/**` 保持基础看板语义不变。高级报表使用单独的 `/api/admin/reports/**` 路径，统一要求管理员 Token。

### 高级经营报表

通用查询参数：

```text
startDate     必填，yyyy-MM-dd，含开始日期
endDate       必填，yyyy-MM-dd，含结束日期
granularity   趋势接口必填，DAY 或 MONTH
airlineId     可选，航司 ID
departureCity 可选，出发城市
arrivalCity   可选，到达城市
limit         榜单接口可选，默认 20，最大 50，必须大于 0
```

日期范围必须满足 `startDate <= endDate`，最大跨度为 366 天。金额字段均为 decimal，订单口径中的活跃机票订单指 `ticket_order.status IN ('ISSUED', 'CHANGED')`。

销售趋势：

```http
GET /api/admin/reports/sales-trend
```

参数：`startDate`、`endDate`、`granularity`。按 `ticket_order.pay_time` 过滤和分组，返回请求范围内每个日/月周期；无数据周期返回 0。

响应 `data[]`：

```json
{
  "period": "2026-06-01",
  "activeOrderCount": 2,
  "passengerCount": 3,
  "revenue": 500.00
}
```

航线表现：

```http
GET /api/admin/reports/route-performance
```

参数：`startDate`、`endDate`、`airlineId`、`departureCity`、`arrivalCity`、`limit`。收入按活跃机票订单的 `ticket_order.pay_time` 过滤，退款按 `refund_record.created_at` 过滤。排序为 `netRevenue` 降序、`activeOrderCount` 降序、`routeLabel` 升序。仅有退款、没有同期收入的航线也会返回，`netRevenue = revenue - refundAmount`。

响应 `data[]`：

```json
{
  "departureCity": "上海",
  "arrivalCity": "北京",
  "routeLabel": "上海 - 北京",
  "activeOrderCount": 2,
  "passengerCount": 3,
  "revenue": 900.00,
  "refundAmount": 100.00,
  "netRevenue": 800.00
}
```

航班客座率：

```http
GET /api/admin/reports/flight-load-factor
```

参数：`startDate`、`endDate`、`airlineId`、`departureCity`、`arrivalCity`、`limit`。按 `flight.departure_time` 过滤，`soldPassengerCount` 只统计活跃机票订单关联的 `order_passenger`，`loadFactorPercent = soldPassengerCount * 100 / totalSeats`，当 `totalSeats = 0` 时返回 0。

响应 `data[]`：

```json
{
  "flightId": 1,
  "flightNo": "MU5101",
  "airlineName": "东方航空",
  "routeLabel": "上海 - 北京",
  "departureTime": "2026-06-05T08:00:00",
  "totalSeats": 30,
  "soldPassengerCount": 24,
  "loadFactorPercent": 80.00
}
```

退票趋势：

```http
GET /api/admin/reports/refund-trend
```

参数：`startDate`、`endDate`、`granularity`。按 `refund_record.created_at` 过滤和分组，返回请求范围内每个日/月周期；无数据周期返回 0。

响应 `data[]`：

```json
{
  "period": "2026-06-01",
  "refundCount": 2,
  "refundAmount": 200.00
}
```

候补表现：

```http
GET /api/admin/reports/waitlist-performance
```

参数：`startDate`、`endDate`、`airlineId`、`departureCity`、`arrivalCity`。按 `waitlist_order.created_at` 过滤；`payAmount` 和 `refundAmount` 使用同一批候补单汇总。状态计数覆盖 `PENDING_PAYMENT`、`WAITING`、`SUCCESS`、`FAILED`、`CANCELLED`、`REFUNDED`、`EXPIRED`，其中 `expiredCount` 表示已过期未支付或未兑现的候补单数量；各状态计数之和应等于 `submittedCount`。

响应 `data`：

```json
{
  "submittedCount": 7,
  "pendingPaymentCount": 1,
  "waitingCount": 1,
  "successCount": 1,
  "failedCount": 1,
  "cancelledCount": 1,
  "refundedCount": 1,
  "expiredCount": 1,
  "payAmount": 910.00,
  "refundAmount": 110.00
}
```
