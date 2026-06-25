# SkyBooker 云航智订

> **SkyBooker 云航智订** 是一个基于 **Next.js + Spring Boot** 的智能航班查询与机票预订系统，覆盖航班检索、选座下单、订单支付、候补、退改签、AI 购票助手和管理后台。

- 中文名：云航智订
- 英文名：SkyBooker
- 仓库名：`skybooker-flight`
- 项目副标题：基于 Next.js + Spring Boot 的智能航班查询与机票预订系统

项目面向 Java Web 实训与课程设计场景，按真实在线航空票务平台的核心流程设计。前端负责页面体验、表单交互、航班展示、座位选择和后台操作；后端负责认证鉴权、业务规则、订单状态流转、座位库存控制、候补兑现、退改签计算、AI 推荐查询和管理统计。

## 核心功能

### 用户端

- 用户认证：邮箱验证码注册、邮箱密码登录、邮箱验证码找回密码、JWT 鉴权；
- 航班查询：按日期、航班号、出发地、目的地检索航班，支持航空公司、时间、价格、飞行时长、直飞/中转、舱位和余票筛选；
- 乘机人与选座：维护乘机人，查看可选、已售、锁定中、不可选座位；
- 订单流程：创建订单、模拟支付、取消订单、查看订单详情和订单列表；
- 退改签：按规则计算退票手续费、改签差价和手续费，并维护订单状态；
- 候补排队：目标舱位无票时提交候补，支付后等待，退票释放座位后自动兑现。

### AI 购票助手

- 支持自然语言输入，例如“我明天想从上海去北京，便宜一点”；
- 解析出发地、目的地、日期、价格偏好和时间偏好；
- 必要信息缺失时主动追问；
- 基于数据库真实航班返回推荐卡片、推荐理由和跳转链接；
- 默认使用规则解析，也可启用兼容 OpenAI Chat Completions 的 LLM 意图解析。

### 管理后台

- 管理员独立登录和管理端 Token；
- 航班新增、修改、上架、下架、生成座位和不可选座位设置；
- 航司、机场、航线等基础资料维护；
- 订单查看、普通用户启停、运营看板和高级报表；
- 用户端 Token 与管理端 Token 隔离，普通用户不能访问后台接口。

## 技术栈

- 前端：Next.js App Router、React、TypeScript、Tailwind CSS、shadcn/ui、lucide-react、React Hook Form、Zod
- 后端：Java 21、Spring Boot 3.3、Spring MVC、Spring Security、MyBatis、MySQL 8、Redis 7、Flyway、JWT、Bean Validation、Knife4j / OpenAPI、Resend
- 部署与测试：Docker、Docker Compose、Nginx、Maven、JUnit / Spring Boot Test、JMeter、Shell smoke scripts

## 项目结构

```text
skybooker-flight/
├── backend/                     # Spring Boot 后端
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/skybooker/  # auth、flight、order、refund、change、waitlist、ai、admin 等模块
│       └── resources/
│           ├── mapper/          # MyBatis XML
│           └── db/migration/    # Flyway 迁移
├── frontend/                    # Next.js 前端应用
├── deploy/nginx/                # Nginx 配置
├── docs/                        # 需求、架构、API、部署、测试、展示文档
├── appendices/                  # 错误码、响应、SQL、Git 规范
├── scripts/                     # 烟测、并发测试、演示数据脚本
├── docker-compose.yml
└── README.md
```

后端按业务模块纵向分包，每个模块按需包含 `controller/`、`service/`、`mapper/`、`entity/`、`dto/`、`vo/` 和 `enums/`。

## 快速启动

推荐使用 Docker Compose 启动。源码开发时再额外安装 JDK 21、Maven 3.8+、Node.js 20+ 和 pnpm。

### 1. 准备环境变量

```bash
cp .env.example .env
```

`.env` 示例：

```env
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DB=flight_booking
MYSQL_TEST_DB=flight_booking_test
MYSQL_USER=root
MYSQL_PASSWORD=replace-with-local-password

REDIS_HOST=localhost
REDIS_PORT=6379

# JWT 签名密钥（至少 256 位）。生成：openssl rand -base64 48 。务必妥善保管，轮换或丢失后所有已签发 token 失效、用户需重新登录。
JWT_SECRET=replace-with-a-random-secret-at-least-256-bits-long
JWT_ACCESS_MS=3600000
JWT_REFRESH_MS=1209600000

OPENAPI_ENABLED=true

MAIL_PROVIDER=log
MAIL_FROM=SkyBooker <noreply@your-domain.com>
RESEND_API_KEY=replace-with-resend-api-key
RESEND_BASE_URL=https://api.resend.com

AI_LLM_ENABLED=false
AI_LLM_BASE_URL=https://api.openai.com/v1
AI_LLM_API_KEY=replace-with-llm-provider-key
AI_LLM_MODEL=gpt-4o-mini
AI_LLM_TIMEOUT_MS=8000
AI_LLM_MAX_RETRIES=1
# 后台写入 LLM 配置时必需：apiKey 加密密钥（openssl rand -base64 32）。缺失或格式非法时应用仍可启动并回退 AI_LLM_* 环境变量默认值，但管理员 PUT /api/admin/ai/llm-config 会失败
AI_CONFIG_ENC_KEY=

BACKEND_PORT=8080
NGINX_PORT=8088
```

`MYSQL_PASSWORD`、`JWT_SECRET`、`RESEND_API_KEY` 和 `AI_CONFIG_ENC_KEY`（后台写入 LLM 配置时必需）应使用真实安全值，并只保存在本地 `.env`、服务器环境变量或部署平台密钥中。默认 `MAIL_PROVIDER=log` 不需要 Resend 凭据；生产发送邮件时再改为 `MAIL_PROVIDER=resend`。

### 2. 启动服务

```bash
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
docker compose logs -f backend
```

访问入口：

```text
http://localhost:8088
```

API 基地址：

```text
http://localhost:8088/api
```

验证：

```bash
curl 'http://localhost:8088/healthz'
curl 'http://localhost:8088/api/flights?page=1&size=1'
```

OpenAPI 启用后可访问：

```text
http://localhost:8088/swagger-ui.html
http://localhost:8088/v3/api-docs
```

### 3. 使用 Docker Hub 镜像

使用已发布到 Docker Hub 的后端镜像时，可将 Compose 中的 `backend` 服务切换为：

```yaml
services:
  backend:
    image: yunluoxincheng/skybooker-backend:latest
    container_name: skybooker-backend
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DB: ${MYSQL_DB:-flight_booking}
      MYSQL_USER: ${MYSQL_USER:-root}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?Set MYSQL_PASSWORD in .env}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET:?Set JWT_SECRET in .env}
      JWT_ACCESS_MS: ${JWT_ACCESS_MS:-3600000}
      JWT_REFRESH_MS: ${JWT_REFRESH_MS:-1209600000}
      OPENAPI_ENABLED: ${OPENAPI_ENABLED:-false}
      MAIL_PROVIDER: ${MAIL_PROVIDER:-log}
      MAIL_FROM: ${MAIL_FROM:-}
      RESEND_API_KEY: ${RESEND_API_KEY:-}
      RESEND_BASE_URL: ${RESEND_BASE_URL:-https://api.resend.com}
      AI_LLM_ENABLED: ${AI_LLM_ENABLED:-false}
      AI_LLM_BASE_URL: ${AI_LLM_BASE_URL:-}
      AI_LLM_API_KEY: ${AI_LLM_API_KEY:-}
      AI_LLM_MODEL: ${AI_LLM_MODEL:-}
      AI_LLM_TIMEOUT_MS: ${AI_LLM_TIMEOUT_MS:-8000}
      AI_LLM_MAX_RETRIES: ${AI_LLM_MAX_RETRIES:-1}
      AI_CONFIG_ENC_KEY: ${AI_CONFIG_ENC_KEY:-}
    ports:
      - "${BACKEND_PORT:-127.0.0.1:8080}:8080"
```

完整部署说明见 `docs/11_DEPLOYMENT_GUIDE.md`。

## 数据库与演示账号

本项目使用 Flyway 自动初始化数据库，迁移脚本位于：

```text
backend/src/main/resources/db/migration/
```

默认演示账号：

```text
普通用户：user1@example.com / User@123456
管理员用户名：admin
管理员密码：Admin@123456
管理员资料邮箱：admin@skybooker.local
```

首次部署后应修改默认管理员密码。演示日期过期时，可运行 `scripts/refresh-demo-flight-dates.sql` 刷新演示航班和订单日期。

## 本地开发

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
pnpm install
pnpm dev
```

前端 API 环境变量建议：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
```

## 测试验证

后端测试：

```bash
cd backend
mvn test
```

部署烟测：

```bash
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
```

同座位并发下单验证：

```text
scripts/jmeter/same-seat-order-race.jmx
scripts/jmeter/run-same-seat-concurrency-report.sh
scripts/concurrency/verify-same-seat-order-race.sh
```

测试策略和报告生成说明见 `docs/12_TESTING_GUIDE.md`。

## 文档索引

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
- `appendices/api-response-convention.md`：统一响应约定
- `appendices/error-code.md`：错误码约定
- `appendices/git-convention.md`：Git 协作约定
- `appendices/sql-convention.md`：SQL 规范
- `frontend/DESIGN.md`：前端 UI/UX 设计规范

## 开发规范

- 后端遵循 `docs/10_BACKEND_DESIGN.md`；
- Controller 负责请求、校验和统一响应；
- Service 负责业务规则、权限上下文和事务边界；
- Mapper 只负责数据库访问；
- Flyway 是唯一的数据库结构和初始化数据来源；
- API 字段使用 `camelCase`，数据库表和字段使用 `lower_snake_case`；
- 金额使用 `DECIMAL` / `BigDecimal`，不使用浮点数。

## 许可证

本项目基于 [MIT License](./LICENSE) 开源。
