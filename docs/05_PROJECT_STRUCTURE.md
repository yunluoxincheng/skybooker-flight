# 05_PROJECT_STRUCTURE：项目结构设计

## 1. 仓库结构

```text
skybooker-flight-system/
├── README.md
├── docs/
├── backend/
├── frontend/
├── docker-compose.yml
├── scripts/
└── .gitignore
```

## 2. 后端结构

```text
backend/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/example/skybooker/
    │   │   ├── SkyBookerApplication.java
    │   │   ├── common/
    │   │   │   ├── api/
    │   │   │   ├── exception/
    │   │   │   ├── pagination/
    │   │   │   └── util/
    │   │   ├── config/
    │   │   ├── auth/
    │   │   ├── user/
    │   │   ├── passenger/
    │   │   ├── airline/
    │   │   ├── airport/
    │   │   ├── flight/
    │   │   ├── seat/
    │   │   ├── order/
    │   │   ├── refund/
    │   │   ├── change/
    │   │   ├── waitlist/
    │   │   ├── ai/
    │   │   └── admin/
    │   └── resources/
    │       ├── application.yml
    │       ├── mapper/
    │       └── db/migration/
    └── test/
```

## 3. 后端分包规范

推荐按业务模块纵向分包，而不是按 Controller、Service、Mapper 横向分包。

示例：

```text
flight/
├── controller/
├── service/
├── mapper/
├── entity/
├── dto/
├── vo/
└── enums/
```

优点：

- 模块边界清晰；
- 多人开发冲突少；
- 查找代码方便；
- 适合复杂业务项目。

## 4. MyBatis Mapper 结构

```text
resources/mapper/
├── UserMapper.xml
├── FlightMapper.xml
├── SeatMapper.xml
├── OrderMapper.xml
├── RefundMapper.xml
├── ChangeMapper.xml
├── WaitlistMapper.xml
└── AiChatMapper.xml
```

## 5. 前端结构

```text
frontend/
├── package.json
├── next.config.ts
├── tsconfig.json
├── tailwind.config.ts
├── DESIGN.md
└── src/
    ├── app/
    ├── components/
    ├── features/
    ├── lib/
    ├── services/
    ├── types/
    └── styles/
```

## 6. 前端 App Router 路由

```text
src/app/
├── page.tsx
├── login/page.tsx
├── register/page.tsx
├── flights/page.tsx
├── flights/[id]/page.tsx
├── booking/[flightId]/page.tsx
├── orders/page.tsx
├── orders/[id]/page.tsx
├── ai-assistant/page.tsx
└── admin/
    ├── page.tsx              # 管理后台登录入口 /admin
    ├── dashboard/page.tsx
    ├── flights/page.tsx
    ├── orders/page.tsx
    └── users/page.tsx
```

## 7. 前端 Features 结构

```text
src/features/
├── auth/
├── flight/
├── booking/
├── order/
├── ai-assistant/
└── admin/
```

每个 feature 内部建议包含：

```text
components/
hooks/
types.ts
utils.ts
```

## 8. Flyway 脚本结构

```text
backend/src/main/resources/db/migration/
├── V1__init_schema.sql
├── V2__init_base_data.sql
├── V3__init_flight_data.sql
├── V4__init_seat_data.sql
├── V5__init_demo_orders.sql
└── V6__add_ai_chat_tables.sql
```

## 9. 文档结构

```text
docs/
├── 01_REQUIREMENTS.md
├── 02_FEATURE_SPEC.md
├── 03_TECH_SELECTION.md
├── 04_ARCHITECTURE.md
├── 05_PROJECT_STRUCTURE.md
├── 06_DATABASE_DESIGN.md
├── 07_API_DESIGN.md
├── 08_AI_CUSTOMER_SERVICE.md
├── 09_FRONTEND_DESIGN.md
├── 10_BACKEND_DESIGN.md
├── 11_DEPLOYMENT_GUIDE.md
├── 12_TESTING_GUIDE.md
├── 13_DEVELOPMENT_PLAN.md
├── 14_PRESENTATION_GUIDE.md
├── 15_AUTH_DESIGN.md
└── 16_STATE_MACHINE.md
```
