# SkyBooker 云航智订

> **SkyBooker 云航智订** 是一个基于 **Next.js + Spring Boot + MyBatis + MySQL + Redis + Flyway** 的智能航班查询与机票预订系统。

- 中文名：云航智订
- 英文名：SkyBooker
- 仓库名：`skybooker-flight-system`
- 项目副标题：基于 Next.js + Spring Boot 的智能航班查询与机票预订系统

本项目面向 Java Web 开发框架实训场景，模拟真实航空票务平台的核心业务流程，提供航班查询、智能购票助手、机票预订、可视化选座、排队候补、退改签、后台管理与运营统计等能力。

项目不是简单的 CRUD 作业，而是按照真实前后端分离项目进行设计：前端负责用户体验与页面交互，后端负责业务规则、库存事务、订单状态流转、数据库查询与安全控制。

## 1. 项目背景

传统人工售票方式存在效率低、信息不透明、用户体验差等问题。随着在线出行服务普及，用户希望能够快速查询航班、筛选价格、查看余票、在线订票、退改签以及获得个性化购票建议。

本项目围绕“航班查询与机票预订”构建完整业务系统，重点体现：

- 航班查询与高级筛选；
- 机票预订与座位库存控制；
- 多人并发订票场景下的事务安全；
- 候补排队购票；
- 在线退票与改签；
- 管理员后台维护航班、订单和普通用户；
- AI 智能购票助手推荐可购买航班。

## 2. 核心功能

### 用户端

- 用户认证：邮箱验证码注册、邮箱密码登录、邮箱验证码找回密码、JWT 鉴权；
- 航班查询：按日期 + 航班号查询，或按日期 + 出发地 + 目的地查询；
- 高级筛选：航空公司、起飞时间、价格区间、飞行时长、直飞/中转、航班状态；
- 航班详情：起降机场、起降时间、飞行时长、票价、余票、准点率、行李额；
- 可视化选座：可选、已售、锁定中、不可选座位；
- 机票预订：选择乘机人、选择座位、提交订单、模拟支付；
- 候补排队：目标舱位无票时提交并支付候补订单，有退票时优先为已支付候补用户出票；
- 退票：根据规则计算手续费并释放座位；
- 改签：选择新航班，计算差价与手续费；
- 我的订单：查看订单、支付、取消、退票、改签。

### AI 智能购票助手

- 支持用户自然语言表达需求，例如“我明天想从上海去北京，便宜一点”；
- 解析用户出发地、目的地、日期、价格偏好、时间偏好等条件；
- 查询数据库中真实可购买航班；
- 返回前三个推荐航班；
- 给出航班详情页、预订页、筛选结果页链接；
- 缺少必要信息时主动追问。

### 管理后台

- 管理员登录；
- 航班管理：新增、修改、上架、下架；
- 航司管理、机场管理；
- 座位管理：生成座位、设置不可选座位；
- 订单管理：查看订单、处理退票和改签；
- 普通用户管理：查看、禁用普通用户，不包含管理员账号管理；
- 数据统计：订单数、销售额、热门航线、订单状态分布。

## 3. 技术栈

### 前端

- Next.js App Router
- React
- TypeScript
- Tailwind CSS
- shadcn/ui
- lucide-react
- React Hook Form
- Zod

### 后端

- Spring Boot
- Spring MVC
- MyBatis
- MySQL
- Redis
- Flyway
- Spring Security
- JWT
- Validation
- Knife4j / Swagger

### 部署

- Docker
- Docker Compose
- Nginx

## 4. 项目结构

```text
skybooker-flight-system/
├── README.md
├── docs/
├── backend/
│   ├── pom.xml
│   ├── src/main/java/com/example/skybooker/
│   └── src/main/resources/db/migration/
├── frontend/
│   ├── package.json
│   ├── DESIGN.md
│   └── src/
├── docker-compose.yml
└── scripts/
```

详细结构见：`docs/05_PROJECT_STRUCTURE.md`。

## 5. 快速启动

### 1. 启动基础服务

```bash
cd skybooker-flight-system

docker compose up -d mysql redis
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动时会自动执行 Flyway 脚本：

```text
backend/src/main/resources/db/migration/
```

### 3. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

访问：

```text
http://localhost:3000
```

后端 API 地址：

```text
http://localhost:8080/api
```

## 6. Flyway 数据库迁移

本项目使用 Flyway 自动初始化数据库，不再手动导入单个 `init.sql`。

迁移脚本位于：

```text
backend/src/main/resources/db/migration/
```

命名格式：

```text
V1__init_schema.sql
V2__init_base_data.sql
V3__init_flight_data.sql
V4__init_seat_data.sql
V5__init_demo_orders.sql
V6__add_ai_chat_tables.sql
```

## 7. 演示重点

建议 PPT 和系统视频重点展示：

1. 航班查询与高级筛选；
2. AI 智能购票助手自然语言推荐航班；
3. 推荐结果点击进入筛选页或预订页；
4. 可视化座位图；
5. 并发订票库存控制；
6. 候补下单支付与退票后自动兑现出票；
7. 后台航班管理与数据统计。

## 8. 文档索引

- `docs/01_REQUIREMENTS.md`：需求分析
- `docs/02_FEATURE_SPEC.md`：功能细化
- `docs/03_TECH_SELECTION.md`：技术选型
- `docs/04_ARCHITECTURE.md`：整体架构
- `docs/05_PROJECT_STRUCTURE.md`：项目结构
- `docs/06_DATABASE_DESIGN.md`：数据库设计
- `docs/07_API_DESIGN.md`：接口设计
- `docs/08_AI_CUSTOMER_SERVICE.md`：AI 智能购票助手
- `docs/09_FRONTEND_DESIGN.md`：前端工程设计
- `docs/10_BACKEND_DESIGN.md`：后端工程设计
- `docs/11_DEPLOYMENT_GUIDE.md`：部署指南
- `docs/12_TESTING_GUIDE.md`：测试指南
- `docs/13_DEVELOPMENT_PLAN.md`：开发计划
- `docs/14_PRESENTATION_GUIDE.md`：展示指南
- `docs/15_AUTH_DESIGN.md`：认证与登录注册设计
- `docs/16_STATE_MACHINE.md`：核心状态机
- `frontend/DESIGN.md`：给 AI 生成 UI 使用的 UI/UX 设计规范

## 认证方案说明

SkyBooker 云航智订采用用户端和管理端隔离的认证方案。

用户端认证：

- 邮箱验证码注册；
- 邮箱密码登录；
- 邮箱验证码找回密码；
- JWT 登录态管理。

管理端认证：

- 管理后台入口为 `/admin`；
- 管理员使用用户名 + 密码登录；
- 管理端 JWT 只允许访问后台接口。

认证设计原则：

- 普通用户通过邮箱验证码完成注册，密码使用 BCrypt 哈希存储；
- 用户端登录使用邮箱 + 密码，只允许普通用户登录；
- 管理后台入口为 `/admin`，管理员使用用户名 + 密码登录；
- 管理员账号同样存储在 `users` 表，通过 `role = ADMIN` 访问管理后台，但不能登录用户端；
- 找回密码通过邮箱验证码校验用户身份；
- 邮箱验证码存储在 Redis 中，默认有效期 5 分钟，并限制发送频率；
- 管理员账号不开放注册，由 Flyway 初始化或后台创建；
- 手机验证码、微信登录、支付宝登录作为扩展能力，不作为实训主流程。

推荐邮件发送方式：开发环境可使用个人邮箱 SMTP 或 Mock 邮件服务，演示/部署环境可使用 Brevo、Resend 等事务邮件服务。短信验证码由于通常涉及付费、签名审核和服务商资质，不作为默认实现。
