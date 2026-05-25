# 09_FRONTEND_DESIGN：前端工程设计

## 1. 前端定位

前端采用 Next.js 构建，负责用户端和管理员端所有页面展示与交互。

重点页面包括：

- 首页；
- 航班查询页；
- AI 智能购票助手页；
- 航班详情页；
- 预订选座页；
- 我的订单页；
- 管理后台。

## 2. 技术栈

```text
Next.js App Router
React
TypeScript
Tailwind CSS
shadcn/ui
lucide-react
React Hook Form
Zod
```

## 3. 路由设计

```text
/
/login
/register
/flights
/flights/[id]
/booking/[flightId]
/orders
/orders/[id]
/ai-assistant
/admin
/admin/dashboard
/admin/flights
/admin/orders
/admin/users
```

## 4. 页面职责

| 页面 | 职责 |
|---|---|
| `/` | 首页，展示搜索入口、热门航线、AI 助手入口 |
| `/flights` | 航班查询与筛选 |
| `/flights/[id]` | 航班详情 |
| `/booking/[flightId]` | 选择乘机人、选座、确认订单 |
| `/orders` | 我的订单列表 |
| `/orders/[id]` | 订单详情、退票、改签 |
| `/ai-assistant` | 智能购票助手聊天页面 |
| `/admin` | 管理后台入口，未登录时展示管理员登录页 |
| `/admin/dashboard` | 管理后台统计看板 |
| `/admin/flights` | 航班管理 |
| `/admin/orders` | 订单管理 |
| `/admin/users` | 普通用户管理 |

`/admin/users` 只展示和操作 `role = USER` 的普通用户账号，不提供管理员账号启停入口。

## 5. 组件结构

```text
src/components/
├── layout/
├── ui/
└── common/

src/features/
├── auth/
├── flight/
├── booking/
├── order/
├── ai-assistant/
└── admin/
```

## 6. AI 助手组件

```text
features/ai-assistant/
├── components/
│   ├── ChatContainer.tsx
│   ├── ChatMessageList.tsx
│   ├── ChatMessageItem.tsx
│   ├── ChatInput.tsx
│   ├── FlightRecommendationCard.tsx
│   ├── QuickActionBar.tsx
│   └── MissingInfoPrompt.tsx
├── hooks/useAiChat.ts
├── types.ts
└── utils.ts
```

## 7. 航班模块组件

```text
features/flight/
├── components/
│   ├── FlightSearchForm.tsx
│   ├── FlightFilterSidebar.tsx
│   ├── FlightSortBar.tsx
│   ├── FlightList.tsx
│   ├── FlightCard.tsx
│   ├── FlightStatusBadge.tsx
│   └── FlightPriceTag.tsx
├── hooks/useFlightSearch.ts
└── types.ts
```

## 8. 订票模块组件

```text
features/booking/
├── components/
│   ├── BookingSteps.tsx
│   ├── PassengerSelector.tsx
│   ├── SeatMap.tsx
│   ├── SeatLegend.tsx
│   ├── PriceSummary.tsx
│   ├── BookingConfirmDialog.tsx
│   └── PaymentMockPanel.tsx
├── hooks/useSeatMap.ts
├── hooks/useBooking.ts
└── types.ts
```

## 9. 后台模块组件

```text
features/admin/
├── components/
│   ├── AdminSidebar.tsx
│   ├── AdminHeader.tsx
│   ├── StatCard.tsx
│   ├── DataTable.tsx
│   └── ConfirmDialog.tsx
├── flight-management/
├── order-management/
├── user-management/
└── dashboard/
```

## 10. API 请求封装

统一封装：

```text
src/lib/request.ts
```

业务 API：

```text
src/services/flightApi.ts
src/services/orderApi.ts
src/services/bookingApi.ts
src/services/aiApi.ts
src/services/adminApi.ts
```

## 11. 类型定义

统一放在：

```text
src/types/
```

例如：

```text
flight.ts
order.ts
ai.ts
user.ts
```

## 12. 状态管理

基础版本不引入复杂全局状态库。

建议：

- 登录状态：localStorage + Context；
- 查询条件：URL Query Params；
- 页面数据：组件内 state 或自定义 hook；
- 表单状态：React Hook Form；
- 校验：Zod。

## 13. UI 设计

详细 UI 设计见：

```text
frontend/DESIGN.md
```

该文档用于指导 AI 生成页面和组件。

## 认证页面设计

前端需要提供以下认证页面：

```text
/login
/register
/forgot-password
```

### 登录页 `/login`

字段：

- 邮箱；
- 密码；
- 登录按钮；
- 注册入口；
- 忘记密码入口。

交互要求：

- 邮箱格式校验；
- 密码不能为空；
- 登录成功后保存 JWT；
- 该页面只允许普通用户登录，管理员账号登录时提示使用管理后台入口。

### 管理后台登录页 `/admin`

该路径是前端页面路由，不是后端 JSON API。所有后台 API 仍使用 `/api/admin/**`。

字段：

- 管理员用户名；
- 密码；
- 登录按钮。

交互要求：

- 调用 `/api/admin/auth/login`；
- 普通用户账号不能登录；
- 登录成功后进入 `/admin/dashboard`；
- 不展示用户注册、忘记密码、用户端入口等普通用户操作。

### 注册页 `/register`

字段：

- 邮箱；
- 发送验证码按钮；
- 邮箱验证码；
- 昵称；
- 密码；
- 确认密码；
- 注册按钮。

交互要求：

- 点击发送验证码后按钮进入 60 秒倒计时；
- 验证码输入 6 位数字；
- 密码和确认密码必须一致；
- 注册成功后跳转登录页或自动登录。

### 忘记密码页 `/forgot-password`

字段：

- 邮箱；
- 发送验证码按钮；
- 验证码；
- 新密码；
- 确认新密码；
- 重置密码按钮。

认证相关组件建议放在：

```text
features/auth/
├── components/
│   ├── LoginForm.tsx
│   ├── RegisterForm.tsx
│   ├── ForgotPasswordForm.tsx
│   └── EmailCodeButton.tsx
├── hooks/
│   └── useAuth.ts
└── types.ts
```
