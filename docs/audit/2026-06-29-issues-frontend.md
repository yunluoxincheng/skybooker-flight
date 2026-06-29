# 前端 Issue 清单 — 前后端契约审查（2026-06-29）

> 来源：[2026-06-29-frontend-backend-contract-review.md](2026-06-29-frontend-backend-contract-review.md)
> 范围：审查中发现的**需前端修复**的问题。后端侧问题见 [2026-06-29-issues-backend.md](2026-06-29-issues-backend.md)。
> 优先级：P0=阻断/核心体验 / P1=明显坏体验 / P2=契约/功能 / P3=低优先级
> 前置条件：后端 [B-CORS-1](2026-06-29-issues-backend.md#b-cors-1) 修复前，所有接口都会 `Failed to fetch`，下列问题无法在浏览器实际复现。

---

## F-AI-1 ｜ AI 助手刷新后历史消息航班卡片/快捷操作全部丢失

- **优先级**：P0
- **现象**：刷新 AI 助手页面后，历史消息里所有航班推荐卡片、快捷操作、追问问题消失，只剩纯文本；新发的消息渲染正常。
- **根因 / 证据**：
  - 前端 `types/ai.ts:AiSessionMessageVO` 声明 `{id,sessionId,role,content,replyType,flights,quickActions,createdAt}`。
  - 后端实际结构 `AiSessionMessageVO` 为 `{role,content,messageType,extra(Map),createdAt}`——**没有** `id/sessionId/replyType/flights/quickActions`。富内容（`replyType/parsedCondition/missingFields/followUpQuestion/searchUrl/flights/quickActions`）后端存在 `extra` 里（`AiChatService.java:317-328`），用 `messageType`（`RECOMMENDATION`/`TEXT`）区分类型。
  - `features/ai/hooks/useAiChat.ts:82-90` 的 `loadHistory` 直接读 `m.id`(无→`undefined`)、`m.replyType/flights/quickActions`(全 `undefined`)。
  - `sendMessage` 不受影响（直接从 `AiChatReplyVO` 构造本地消息）。
- **建议修复**：
  1. `types/ai.ts:AiSessionMessageVO` 改为后端真实结构：`{role, content, messageType, extra: Record<string, unknown>, createdAt}`。
  2. `loadHistory` 从 `m.extra` 取 `replyType/flights/quickActions/missingFields/followUpQuestion/searchUrl`；`messageType==='RECOMMENDATION'` 时映射 `replyType='FLIGHT_RECOMMENDATION'`（与 `AiReplyType` 对齐）。
  3. `id` 不要用 `String(m.id)`（恒 `undefined` 导致 React key 撞车），改用 `index` 或 `${createdAt}-${role}`。
- **验证**：在 AI 助手产生一条航班推荐后刷新页面，历史中该消息仍显示航班卡片与快捷操作。

---

## F-CHANGE-1 ｜ 已出票订单"改签"按钮 404，改签功能未接入

- **优先级**：P1
- **现象**：已出票（`ISSUED`）订单详情页点"改签"跳转 `/orders/{id}/change` → 404；改签相关接口在前端零调用。
- **根因 / 证据**：
  - `app/orders/[id]/page.tsx:187` 渲染 `<a href={/orders/${id}/change}>`，但 `app/orders/` 下只有 `[id]/page.tsx`，**无 `[id]/change/` 路由**。
  - `services/orderApi.ts` 的 `getChangeOptions` / `changeOrder` 在整个 `app/`/`features/`/`components/` **零调用**（改签页面未实现）。
  - 后端接口齐全：`GET /api/orders/{id}/change-options`、`POST /api/orders/{id}/change`。
- **建议修复**（二选一）：
  - **方案 A**：补 `app/orders/[id]/change/page.tsx`，用 `getChangeOptions` 渲染可选航班，选中后用 `changeOrder`（入参 `{newFlightId, seatMappings:[{passengerId,newSeatId}]}`）提交，成功后跳回订单详情。
  - **方案 B（临时）**：若改签暂不做，先隐藏该按钮，避免 404。
- **注意**：`changeOrder` 后端返回 `ChangeOrderResultVO`（非 `OrderVO`），订单详情页 `doAction` 已丢弃返回值重新拉单，不受影响；但若新页面要用返回值渲染，需按真实类型处理（见 F-TYPE-1）。

---

## F-TYPE-1 ｜ 多处返回类型声明与后端不符（类型欺诈）

- **优先级**：P2（当前部分靠"丢弃返回值"未炸，但类型错误、易踩坑）
- **问题清单**：

| 函数 | 前端声明 | 后端实际 | 当前是否触发 |
|---|---|---|---|
| `orderApi.refundOrder` | `OrderVO` | `RefundVO{id,orderId,refundAmount,feeAmount,status,createdAt}` | 否（详情页丢弃返回值）；但退票金额/手续费前端拿不到 |
| `orderApi.changeOrder` | `OrderVO` | `ChangeOrderResultVO{id,orderNo,status,flightId,totalAmount,passengers}` | 否（未接入页面） |
| `adminApi.publishFlight` | `FlightVO` | `Void` | 否（操作后重新拉列表） |
| `adminApi.unpublishFlight` | `FlightVO` | `Void` | 同上 |
| `adminApi.generateSeats` | `FlightSeatVO[]` | `Void` | 同上 |

- **证据**：`services/orderApi.ts:36,46`；`services/adminApi.ts:62,66,70`；后端 Controller 返回 `ApiResponse<Void>` / `ApiResponse<RefundVO>` / `ApiResponse<ChangeOrderResultVO>`。
- **建议修复**：把声明改为后端真实类型（`refundOrder→RefundVO`、`changeOrder→ChangeOrderResultVO`、三个 admin 操作→`null`）；如需展示退票金额，新增 `types/order.ts:RefundVO` 并在退票成功后展示 `refundAmount/feeAmount`。
- **关联**：F-CHANGE-1 改签页面要用 `ChangeOrderResultVO`。

---

## F-WAITLIST-1 ｜ 候补功能完全未接入 UI，且 DTO 缺必填字段

- **优先级**：P2（功能缺失 + 类型错误）
- **现象**：`services/waitlistApi.ts` 在 `app/`/`features/`/`components/` 零调用 → 候补（满员排队）功能前端无任何入口。
- **根因 / 证据**：
  - 前端 `types/waitlist.ts:CreateWaitlistDTO` 为 `{flightId, passengerIds}`，**缺 `cabinClass`**；后端 `CreateWaitlistDTO.cabinClass` 为 `@NotNull` 必填 → 一旦做页面，创建 100% 被拒（`VALIDATION_ERROR`）。
  - `types/waitlist.ts:WaitlistVO` 字段大面积不符：前端多 `userEmail/notifiedAt`（后端无），后端多 `cabinClass/payAmount/paidAt/ticketOrderId/refundAmount/passengers[]`（前端无）。
- **建议修复**：
  1. 若产品要做候补：补候补页面（航班详情无座时入口 → 选舱等 `cabinClass` + 乘机人 → `createWaitlist` → 我的候补列表 → 支付/取消）。
  2. `CreateWaitlistDTO` 加 `cabinClass: "ECONOMY"|"BUSINESS"|"FIRST"`；`WaitlistVO` 按后端真实字段重写。
  3. 后端接口齐全：`POST /api/waitlist`、`GET /api/waitlist/my`、`GET /api/waitlist/{id}`、`POST /api/waitlist/{id}/pay`、`POST /api/waitlist/{id}/cancel`。
- **若暂不做**：删除未使用的 `waitlistApi.ts`/`types/waitlist.ts` 死代码，避免误导。

---

## F-REPORT-1 ｜ admin 报表 VO 类型字段大面积错位（做报表页前必修）

- **优先级**：P2（当前无报表页面调用，属"封装但未用"；一旦做报表页全部渲染空白）
- **问题清单**：

| VO | 前端字段 | 后端字段 | 错位 |
|---|---|---|---|
| `SalesTrendVO` | `date, orderCount, revenue` | `period, activeOrderCount, passengerCount, revenue` | `date→period`、`orderCount→activeOrderCount` |
| `RoutePerformanceVO` | `route, orderCount, loadFactor, avgRevenue` | `departureCity, arrivalCity, routeLabel, activeOrderCount, passengerCount, revenue, refundAmount, netRevenue` | `route→routeLabel`、`loadFactor`后端无、`avgRevenue→netRevenue` |
| `FlightLoadFactorVO` | `flightNo, route, totalSeats, occupiedSeats, loadFactor` | `flightId, flightNo, airlineName, routeLabel, departureTime, totalSeats, soldPassengerCount, loadFactorPercent` | `route→routeLabel`、`occupiedSeats→soldPassengerCount`、`loadFactor→loadFactorPercent` |
| `RefundTrendVO` | `date, refundCount, refundAmount` | `period, refundCount, refundAmount` | `date→period`（图表 X 轴会空） |
| `WaitlistPerformanceVO` | `date, waitlistCount, convertedCount, conversionRate` | `submittedCount, pendingPaymentCount, waitingCount, successCount, …, payAmount, refundAmount` | 字段完全不同 |

- **附加**：`adminApi.getWaitlistPerformance` 声明返回 `WaitlistPerformanceVO[]`（数组），后端返回单个对象 `WaitlistPerformanceVO` → 若按数组 `.map()` 会运行时报错。
- **建议修复**：`types/admin.ts` 全部按后端 VO 真实字段重写；`getWaitlistPerformance` 返回类型改为单对象；做报表页时图表 `dataKey`/X 轴字段用后端真实字段名。
- **证据**：后端 `admin/vo/*.java`。

---

## F-ADMIN-1 ｜ 管理员信息 VO 字段不符（`nickname` ↔ `realName`）

- **优先级**：P2
- **现象**：管理端顶栏若显示管理员昵称会空白。
- **根因 / 证据**：前端 `types/auth.ts:AdminUser` 为 `{id,username,nickname,role,status,createdAt}`；后端 `admin/vo/AdminVO` 为 `{id,userId,username,realName,role}`（`nickname`→`realName`，无 `status/createdAt`）。`AdminAuthContext` 存的是 `getAdminMe()` 返回值。
- **建议修复**：`AdminUser` 改为 `{id,userId,username,realName,role}`；UI 显示昵称处改用 `admin.realName`。
- **验证**：管理员登录后顶栏显示真实姓名。

---

## F-AUTH-1 ｜ `register`/`resetPassword` 类型缺 `confirmPassword`，靠隐式透传

- **优先级**：P2（隐患，当前能跑纯属巧合）
- **现象**：注册/改密当前可成功，但类型声明缺字段，重构即坏。
- **根因 / 证据**：`services/authApi.ts` 的 `register`/`resetPassword` 签名只声明 `{email,code,nickname,password}` / `{email,code,newPassword}`，后端 `RegisterDTO/ResetPasswordDTO.confirmPassword` 为 `@NotBlank` 必填。当前因 `RegisterForm`/`ForgotPasswordForm` 把整个 RHF 表单对象（含 `confirmPassword`）一路透传到 `post()`，`JSON.stringify` 把它带上了，才侥幸通过。
- **建议修复**：两个函数签名显式加 `confirmPassword: string`，并在调用处显式构造请求体（不依赖对象透传）。
- **验证**：注册、忘记密码两个流程端到端走通。

---

## F-USER-1 ｜ `UserVO`/`directFlag` 类型与后端不符（低风险）

- **优先级**：P3
- **问题**：
  - 前端 `types/auth.ts:User` 多 `avatar/status/createdAt`，后端 `UserVO` 只有 `{id,email,nickname,role}` → UI 读取这些字段为 `undefined`。
  - 前端 `types/flight.ts:FlightVO.directFlag: boolean`，后端 `Integer` → 显示靠 `0/1` truthy 碰巧正确；admin 编辑 `setValue("directFlag", f.directFlag)` 传数字给 boolean checkbox 属边界行为。
- **建议修复**：`User` 去掉后端不返回的字段（或后端补）；`FlightVO.directFlag` 用法处包 `Boolean(f.directFlag)`，类型改 `number`。

---

## F-SEARCH-1 ｜ 航班搜索航司筛选 `Number(airlineCode)` 产生 NaN

- **优先级**：P3（当前搜索 UI 无航司筛选入口，默认不触发）
- **现象**：URL 带 `airlineCode=CA` 时，请求会带 `airlineId=NaN`，后端 `Long` 解析失败 → 400。
- **根因 / 证据**：`features/flights/hooks/useFlightSearch.ts:60` `if (airlineCode) params.airlineId = Number(airlineCode)`；`Number("CA")===NaN`，`request.ts` 的 `get` 把 `NaN` 转成 `"NaN"` 加入 query。
- **建议修复**：航司筛选要么传编码对应后端的 `airlineId`（需 UI 提供数字 ID 或后端支持按 `airlineCode` 查），要么先移除该分支直到 UI 实现。

---

## F-ENV-1 ｜ 前端无 `.env`，生产环境 `NEXT_PUBLIC_API_BASE_URL` 缺失

- **优先级**：P0（部署阻断，配合后端 CORS）
- **现象**：前端目录无 `.env*`，`lib/request.ts:4` 回退硬编码 `http://localhost:8080/api`；生产部署会直连 localhost，连不上后端。
- **建议修复**：
  1. 提供 `.env.production`：`NEXT_PUBLIC_API_BASE_URL=https://skybooker.yunluostar.com/api`（若前后端同域经 nginx 反代，可用相对路径 `/api`）。
  2. 开发用 `.env.development` 或 `.env.local`：`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api`。
  3. `next.config.ts` 视部署形态决定是否加 `rewrites` 代理（同域反代可不加）。
  4. `.env.example` 列出变量供协作。
- **依赖**：后端 [B-CORS-1](2026-06-29-issues-backend.md#b-cors-1) 放行对应 Origin 后，前端该域名才可达。
- **文档**：在 `docs/11_DEPLOYMENT_GUIDE.md` 补前端环境变量说明。

---

## 阻塞依赖（前端无需改，等后端）

- 订单详情/列表航班信息空白 → 阻塞于后端 [B-ORDER-1](2026-06-29-issues-backend.md#b-order-1)。前端 `types/order.ts:42-46` 已预留字段，后端补齐后自动生效。
- admin 编辑航班回填 → 阻塞于后端 [B-FLIGHT-1](2026-06-29-issues-backend.md#b-flight-1)。前端回填逻辑已就绪，待后端返回数字 ID。

---

## 排期建议

| Issue | 优先级 | 工作量 | 依赖 |
|---|---|---|---|
| F-AI-1 | P0 | 小（类型 + loadHistory） | 无 |
| F-CHANGE-1 | P1 | 中（新增改签页）或 小（临时隐藏） | 无 |
| F-ENV-1 | P0(部署) | 小 | 后端 B-CORS-1 |
| F-TYPE-1 | P2 | 小 | 无 |
| F-AUTH-1 | P2 | 小 | 无 |
| F-ADMIN-1 | P2 | 小 | 无 |
| F-WAITLIST-1 | P2 | 大（功能）或 小（删死码） | 无 |
| F-REPORT-1 | P2 | 小（类型）/ 大（报表页） | 无 |
| F-USER-1 | P3 | 小 | 无 |
| F-SEARCH-1 | P3 | 小 | 无 |
