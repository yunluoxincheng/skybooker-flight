# 前端 ↔ 后端接口契约审查报告

- **审查日期**：2026-06-29
- **审查对象**：`frontend/`（Next.js 16 + React 19）API 调用层、类型层、关键 hooks/页面 ↔ `backend/`（Spring Boot）11 个 Controller 及其 DTO/VO/响应封装
- **审查方法**：前端 `services/*` + `types/*` 逐条对照后端 Controller `@RequestMapping` + DTO/VO 字段；读前端实际调用点（hooks、订单页、admin 页、AI hook）确认每个类型不符**是否在运行时真实触发**；最后对运行中的前后端做 curl + CORS 预切实测
- **基线**：后端 `mvn` 构建产物运行于 `127.0.0.1:8080`，前端 `next dev` 运行于 `*:3000`，MySQL/Redis 已起
- **结论性质**：纯静态对照 + 运行时实测，未修改任何代码

## 总体结论

**BLOCK —— 前端当前完全无法与后端通信**，根因是后端从未配置 CORS。修复 CORS 前，所有 API 调用都会在浏览器跨域预检阶段失败（`Failed to fetch`），此前端等于"只能看的静态壳"，无法触发任何业务逻辑。

CORS 修复后，另有 **3 个 P0 契约不一致**会让核心页面直接显示错误或空白，以及一批 P1/P2 类型契约脱节问题。

| 维度 | 数量 |
|---|---|
| 阻断级（P0） | 4（CORS 阻断 + 订单航班信息空白 + AI 历史丢内容 + admin 编辑航班不可用） |
| 明显坏体验（P1） | 1（改签按钮 404） |
| 类型契约脱节/功能未接入（P2） | 7 |
| 类型隐患/低优先级（P3） | 4 |

> Issue 已按职责拆分：后端问题见 [2026-06-29-issues-backend.md](2026-06-29-issues-backend.md)，前端问题见 [2026-06-29-issues-frontend.md](2026-06-29-issues-frontend.md)。

## 审查方法

1. 逐个 `services/*.ts` 的 URL / HTTP 方法 / 请求体字段，对照后端 Controller 的 `@RequestMapping` 与 DTO（`@NotNull/@NotBlank` 约束）
2. 逐个 `types/*.ts` 字段，对照后端 VO（Lombok `@Data` 字段）
3. 读前端实际调用点，确认每个类型不符**是否真的在运行时触发故障**（区分"类型标错但碰巧能跑"与"确实会坏"）
4. `grep` 确认 waitlist / change / admin-reports 在 `app/` 下的调用情况
5. 对运行中的前后端实测：直连后端、模拟浏览器跨域预检、核对 `GlobalExceptionHandler` + `SecurityConfig` + `SecurityExceptionHandler` 的 401/CORS 链路

## 0. 阻断级发现：后端未配置 CORS（整个前端瘫痪）

### 现象
用户在浏览器用管理员/示例账户登录，前端报 `Failed to fetch`。`fetch()` 抛该错代表**请求未到达后端业务层**（网络层/跨域层失败）。

### 铁证（运行时实测，2026-06-29）

| 测试 | 请求 | 结果 | 结论 |
|---|---|---|---|
| 直连后端 | `POST /api/auth/login`（无 Origin） | `401 {"code":10007,"邮箱或密码错误"}` | **后端业务完全正常** |
| 跨域预检（登录） | `OPTIONS /api/auth/login` + `Origin:localhost:3000` | `403 "Invalid CORS request"`，**无 `Access-Control-Allow-Origin`** | 预检失败 |
| 跨域预检（航班） | `OPTIONS /api/flights` + Origin | `403`，**无 CORS 头** | 连公开接口也被挡 |
| 跨域真实请求 | `POST` + Origin | 返回业务 401 但**无 CORS 响应头** | 浏览器仍拦截响应 |
| 后端源码 grep | `Cors / @CrossOrigin / addCorsMappings / CorsConfigurationSource / .cors(` | **零匹配** | 后端从未配置 CORS |

### 根因链
- 前端 dev 在 `http://localhost:3000`，后端在 `http://localhost:8080`，不同端口 = 浏览器跨域。
- 前端所有 POST/PUT 带 `Content-Type: application/json` → 浏览器先发 OPTIONS 预检 → 后端 `SecurityConfig` 未启用 CORS，Spring 判定 `Invalid CORS request` 返回 403 且无 CORS 头 → **浏览器连真正的请求都不发** → `fetch()` 直接抛 `TypeError: Failed to fetch`。
- 影响范围：**全部 API**（登录、注册、航班搜索、订单、AI、admin）。CORS 在鉴权之前拦截，`permitAll` 的公开接口同样过不去。

### 归属
**后端问题**，不是前端写法错误。前端跨域调用本身（`BASE_URL` 指向 8080）写法正确。详见后端 Issue `B-CORS-1`。允许域名已确认：`http://localhost:3000`、`https://skybooker.yunluostar.com`。

## 1. 基础设施契约对照（这些是**正确**的，给予确认）

| 维度 | 前端实现 | 后端实现 | 结论 |
|---|---|---|---|
| URL 前缀拼接 | `BASE_URL=http://localhost:8080/api` + service 路径 `/auth/login` | Controller 类级 `@RequestMapping("/api/auth")` | ✅ 拼接后完全一致 |
| 响应封装 | `ApiResponse{code,message,data}`，`code!==200` 判失败 | `ApiResponse{int code,String message,T data}`，success=`200` | ✅ 一致 |
| 分页结构 | `PageData{records,total,page,size}` | `PageResponse{records,total(long),page(int),size(int)}` | ✅ 字段一致 |
| 401 处理 | `res.status===401` → 清本地存储 + 抛错 | `SecurityExceptionHandler.commence` → HTTP 401；`resolveHttpStatus` 把 `UNAUTHORIZED/TOKEN_INVALID/TOKEN_EXPIRED/REFRESH_TOKEN_INVALID/INVALID_CREDENTIALS` 映射为 401 | ✅ 链路正确 |
| 认证头 | `Authorization: Bearer <token>`，用户端/管理端分键存储 | `JwtAuthenticationFilter.extractToken` 读 `Bearer` 头 | ✅ 一致 |
| 入口分离 | 用户 `/auth/login`，管理员 `/admin/auth/login` | `AuthController` / `AdminAuthController` 分离，`SecurityConfig` 按 portal 隔离 | ✅ 一致 |

> 即：骨架（URL、封装、分页、认证、401）设计正确。问题集中在**字段级契约**与**跨域/配置**。

## 2. 各模块契约对照与问题

### 2.1 Auth
- URL/方法/请求体全部匹配。
- **`UserVO` 字段少于前端**：后端 `{id,email,nickname,role}`，前端 `User` 多声明 `avatar/status/createdAt` → UI 读取为 `undefined`（P3）。
- **`register`/`resetPassword` 类型缺 `confirmPassword`**：后端 `RegisterDTO/ResetPasswordDTO.confirmPassword` 为 `@NotBlank` 必填，前端 `services/authApi.ts` 签名只声明 4 字段。**当前能注册成功纯属巧合**——表单对象一路透传到 `post()`，`JSON.stringify` 把 `confirmPassword` 带上了。一旦按 service 类型重构即 100% 失败（P2 隐患）。

### 2.2 Flight
- URL/方法匹配。
- **`directFlag` 类型不符**：后端 `Integer`，前端 `boolean`。显示 `flight.directFlag ? "直飞" : "经停"` 靠 `0/1` 的 truthy 碰巧正确；admin 编辑回填 `setValue("directFlag", f.directFlag)` 传入数字给 boolean checkbox，属边界行为（P3）。
- **`FlightVO` 缺 `airlineId/departureAirportId/arrivalAirportId`**（后端只返回 `airlineCode/airportCode`）→ admin 编辑航班无法回填这些数字 ID（见 P0）。
- `FlightSearchParams` 缺后端支持的 `flightNo/maxDurationMinutes/passengerCount/cabinClass` 等（前端少传不影响，非 bug）。
- `sort` 参数：前端拼 `${sortBy}_${order}`（如 `price_asc`），后端 `FlightSort.fromParam` 做 `toUpperCase()` 后匹配枚举 → ✅ 实际工作。

### 2.3 Order / Change / Refund
- **订单详情/列表航班信息空白（P0）**：前端 `OrderVO` 声明并使用 `airlineName/departureCity/arrivalCity/departureTime/arrivalTime`（`app/orders/[id]/page.tsx:235,239,243-248`、`app/orders/page.tsx:171-184`），但**后端 `OrderVO` 完全没有这些字段**（只有 `flightNo`）→ 订单页航司显示 `—`、城市显示 `—`、起降时间整块不渲染。
- **`refundOrder` 返回类型错误**：后端返回 `RefundVO{id,orderId,refundAmount,feeAmount,status,createdAt}`，前端声明 `OrderVO`。**当前不影响**：订单详情页 `doAction` 操作后丢弃返回值、重新 `fetchOrder()`（`orders/[id]/page.tsx:75`）。但退票成功后用户看不到实际退款金额/手续费。
- **`changeOrder` 返回类型错误**：后端返回 `ChangeOrderResultVO`（字段子集），前端声明 `OrderVO`。改签功能前端**完全未接入 UI**（见下）。
- `CreateOrderDTO{flightId,items[{passengerId,seatId}]}` 与后端一致 ✅。

### 2.4 Passenger
- URL/方法/请求体/VO 全部一致 ✅。无问题。

### 2.5 Waitlist
- **功能完全未接入 UI**：`services/waitlistApi.ts` 在 `app/`/`features/`/`components/` 零调用。
- 即便如此类型也不符：前端 `CreateWaitlistDTO` 缺后端 `@NotNull` 的 **`cabinClass`**（一旦做页面，创建 100% 被拒）；`WaitlistVO` 字段大面积不符（前端多 `userEmail/notifiedAt`，后端多 `cabinClass/payAmount/paidAt/ticketOrderId/refundAmount/passengers[]`）。

### 2.6 AI
- **历史消息富内容丢失（P0）**：前端 `AiSessionMessageVO` 声明 `{id,sessionId,role,content,replyType,flights,quickActions,createdAt}`，后端真实是 `{role,content,messageType,extra(Map),createdAt}`。后端把 `replyType/parsedCondition/missingFields/followUpQuestion/searchUrl/flights/quickActions` 全塞进 `extra`（`AiChatService.java:317-328`），用 `messageType`（RECOMMENDATION/TEXT）区分。
  - `useAiChat.ts:82-90` 的 `loadHistory` 读 `m.id`（无→`undefined`，React key 全撞）、`m.replyType/flights/quickActions`（全 `undefined`）→ **刷新页面后历史里所有航班推荐卡片/快捷操作/追问全消失，只剩纯文本**。
  - 新消息（`sendMessage`）正常，因直接从 `AiChatReplyVO` 构造、不经历史结构。
- `AiChatReplyVO` 后端用 `List<Map<String,Object>>`（flights）/`List<Map<String,String>>`（quickActions），前端按强类型 `FlightVO[]`/`QuickAction[]` 取值，依赖后端实际塞的结构，类型层面不一致（P2）。

### 2.7 Admin
- **admin 编辑航班不可用（P0）**：见 2.2，`FlightVO` 不返回数字 ID，`admin/flights/page.tsx:100-113` 无法回填航司/机场 ID，zod 校验阻止提交（代码注释自述）。
- **5 个报表 VO 字段大面积错位**（前端无报表页面调用，属"封装但未用"）：`SalesTrendVO`(`date→period`,`orderCount→activeOrderCount`)、`RoutePerformanceVO`(`route→routeLabel`,`loadFactor`后端无,`avgRevenue→netRevenue`)、`FlightLoadFactorVO`(`route→routeLabel`,`occupiedSeats→soldPassengerCount`,`loadFactor→loadFactorPercent`)、`RefundTrendVO`(`date→period`)、`WaitlistPerformanceVO`(字段完全不同)。
- **`getWaitlistPerformance` 返回结构不符**：前端声明数组 `WaitlistPerformanceVO[]`，后端返回单个对象 `WaitlistPerformanceVO`。
- **`AdminUser` ↔ `AdminVO` 不符**：前端 `nickname`，后端 `realName`；前端 `status/createdAt` 后端无 → 管理端顶栏显示管理员名字可能空白。
- `publishFlight/unpublishFlight/generateSeats` 后端返回 `Void`，前端声明 `FlightVO/FlightSeatVO[]`。**当前不影响**：admin 页操作后都 `fetchFlights()` 重新拉（`admin/flights/page.tsx:150,160`）。
- `DashboardSummaryVO/HotRouteVO/OrderStatusDistributionVO` 字段一致 ✅；`UserAdminVO` 前端是后端子集 ✅。
- admin `/ai/llm-config` 接口前端未对接（无页面），属功能未覆盖，非 bug。

## 3. 问题汇总表（按优先级）

| # | 优先级 | 模块 | 问题 | 归属 |
|---|---|---|---|---|
| 1 | P0 | 跨域 | 后端未配置 CORS，整个前端 `Failed to fetch` | 后端 |
| 2 | P0 | Order | OrderVO 缺航司名/城市/起降时间，订单页航班信息空白 | 后端补字段 / 前端改查 |
| 3 | P0 | AI | 历史消息富内容丢失（读 `extra` 之外的顶层字段） | 前端 |
| 4 | P0 | Admin | 编辑航班无法回填航司/机场 ID，无法提交 | 后端补字段 / 前端改 |
| 5 | P1 | Change | 已出票订单"改签"按钮指向不存在的路由，404 | 前端 |
| 6 | P2 | Change/Refund | 返回类型声明错误（OrderVO vs ChangeOrderResultVO/RefundVO） | 前端 |
| 7 | P2 | Waitlist | 功能未接入 UI + `CreateWaitlistDTO` 缺 `cabinClass` | 前端 |
| 8 | P2 | Admin | 5 个报表 VO 字段错位 + `waitlist-performance` 数组/对象不符 | 前端 |
| 9 | P2 | Admin | `AdminUser.nickname` ↔ `AdminVO.realName` 不符 | 前端 |
| 10 | P2 | Auth | `register/resetPassword` 类型缺 `confirmPassword`，靠隐式透传 | 前端 |
| 11 | P3 | Auth/Flight | `UserVO` 缺 `avatar/status/createdAt`；`directFlag` boolean↔Integer | 前端 |
| 12 | P3 | Flight | `useFlightSearch` 航司筛选 `Number(airlineCode)` → NaN | 前端 |
| 13 | P0(部署) | 配置 | 前端无 `.env`/`NEXT_PUBLIC_API_BASE_URL`，生产回退 localhost | 前端 |

## 4. 验证方式说明

- 本报告所有结论均来自**源码静态对照 + curl/CORS 实测**，未运行前端单测/未启动浏览器 E2E。
- 后端字段以 Java VO/DTO 类的 Lombok 字段为唯一真相（MyBatis 不会映射未声明字段）。
- "当前不影响"的判定基于实际调用点代码（操作后是否用返回值），不是猜测。
- 修复 CORS 后，建议对 P0/P1 项做一次真实浏览器端到端验证（登录、查航班、下单、AI 对话、admin 编辑航班）。

## 相关文档与 Issue

- 后端 Issue：[2026-06-29-issues-backend.md](2026-06-29-issues-backend.md)（CORS、OrderVO/FlightVO 字段补齐）
- 前端 Issue：[2026-06-29-issues-frontend.md](2026-06-29-issues-frontend.md)（AI 历史、改签 404、类型契约清理、.env）
- 接口设计基线：`docs/07_API_DESIGN.md`、`docs/10_BACKEND_DESIGN.md`
- 错误码：`appendices/error-code.md`
