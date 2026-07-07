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
- 航班新增、修改、上架、下架、舱位配置(经济/公务/头等舱价格与座位数)、生成座位和不可选座位设置；
- 航司、机场、航线等基础资料维护；
- 订单查看、普通用户启停、运营看板和高级报表；
- 用户端 Token 与管理端 Token 隔离，普通用户不能访问后台接口。

## 技术栈

- 前端：Next.js App Router、React、TypeScript、Tailwind CSS、shadcn/ui、lucide-react、React Hook Form、Zod
- 后端：Java 21、Spring Boot 3.3、Spring MVC、Spring Security、MyBatis、MySQL 8、Redis 7、Flyway、JWT、Bean Validation、Knife4j / OpenAPI、Resend
- 部署与测试：Docker、Docker Compose、Nginx、GitHub Actions、GHCR / Docker Hub、Maven、JUnit / Spring Boot Test、JMeter、Shell smoke scripts

## 项目结构

```text
skybooker-flight/
├── .github/workflows/           # CI 与镜像构建发布工作流
├── backend/                     # Spring Boot 后端
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/skybooker/  # auth、flight、order、refund、change、waitlist、ai、admin 等模块
│       └── resources/
│           ├── mapper/          # MyBatis XML
│           └── db/migration/    # Flyway 迁移
├── frontend/                    # Next.js 前端应用
│   ├── Dockerfile
│   └── src/
├── deploy/                      # 生产部署模板
│   ├── docker-compose.prod.yml
│   └── nginx/prod.conf
├── docs/                        # 需求、架构、API、部署、测试、展示文档
├── appendices/                  # 错误码、响应、SQL、Git 规范
├── scripts/                     # 部署脚本、烟测、并发测试、演示数据脚本
│   └── deploy.sh
├── docker-compose.yml           # 本地开发 / 演示 Compose
└── README.md
```

后端按业务模块纵向分包，每个模块按需包含 `controller/`、`service/`、`mapper/`、`entity/`、`dto/`、`vo` 和 `enums/`。

## 快速启动

如果只是想在一台已经安装 Docker 的 Linux 服务器上最快跑起完整系统，可以直接执行：

```bash
curl -fsSL https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/main/scripts/deploy.sh \
  | sudo bash
```

脚本会自动在 `/opt/skybooker` 准备生产 `docker-compose.yml`、nginx 配置和 `.env`，生成 `MYSQL_PASSWORD`、`JWT_SECRET`、`AI_CONFIG_ENC_KEY`，然后拉取镜像并启动 MySQL、Redis、backend、frontend、nginx。默认访问：

```text
http://<server-ip>:8088
```

生产正式上线建议使用固定 tag 或 commit SHA，完整说明见 `docs/11_DEPLOYMENT_GUIDE.md`。

SkyBooker 当前支持两条 Docker 路径：

- 本地开发：使用仓库根目录 `docker-compose.yml`，本地构建后端并启动 MySQL、Redis、backend、nginx（nginx 仅作 API 网关，根路径不提供前端 UI）。前端通过 `cd frontend && pnpm dev` 热重载运行，访问 `http://localhost:3000`。
- 生产部署 / 全栈验收：使用 `scripts/deploy.sh`——它会自动准备生产 compose、nginx 配置（把 `deploy/nginx/prod.conf` 落地为 `default.conf`）和 `.env`，再拉取镜像启动 MySQL、Redis、backend、frontend、nginx all-in-one 栈，访问 `http://localhost:8088` 即可见前端 UI。完整流程见 `docs/11_DEPLOYMENT_GUIDE.md`。

### 1. 本地开发 / 演示启动

源码开发建议安装 JDK 21、Maven 3.8+、Node.js 24+、pnpm 10+、Docker Engine 或 Docker Desktop。

准备环境变量：

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

启动本地 Compose：

```bash
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
docker compose logs -f backend
```

本地 compose 的 nginx 是 API 网关，根路径只返回引导文本、不提供前端 UI：

```text
http://localhost:8088            # API 网关：/api/**、/healthz（根路径无前端 UI）
http://localhost:3000            # 前端 UI：cd frontend && pnpm dev
```

API 基地址：

```text
http://localhost:8088/api
http://localhost:8080/api        # 后端直连
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

### 2. 生产 Docker 部署

生产部署推荐使用固定 tag 或 commit SHA。不要直接执行 `curl | sudo bash`；建议先下载脚本、检查内容，再执行。

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
less deploy.sh
chmod +x deploy.sh
sudo ./deploy.sh install --ref "$DEPLOY_REF" --tag sha-<commit-sha>
```

默认安装目录：

```text
/opt/skybooker
```

脚本会创建部署目录、下载生产 Compose 与 nginx 模板、生成 `.env`、拉取镜像并启动：

```text
mysql
redis
backend
frontend
nginx
```

默认镜像源是 GHCR：

```text
ghcr.io/yunluoxincheng/skybooker-flight/skybooker-backend
ghcr.io/yunluoxincheng/skybooker-flight/skybooker-frontend
```

如果 GHCR 镜像是 private，需要先在服务器登录 GHCR。也可以切换到 Docker Hub：

```bash
sudo ./deploy.sh update \
  --image-source dockerhub \
  --dockerhub-namespace <dockerhub-namespace>
```

生产入口默认是：

```text
http://<server-ip>:8088
```

域名和 HTTPS 推荐由宿主机 nginx、云负载均衡或其他边缘代理负责，再反代到 `127.0.0.1:8088`。

常用运维命令：

```bash
cd /opt/skybooker

# 更新到当前 IMAGE_TAG，默认 latest
sudo ./deploy.sh update

# 更新到指定不可变镜像 tag
sudo ./deploy.sh update --tag sha-<commit-sha>

# 回滚到上一个已知正常 tag
sudo ./deploy.sh rollback --tag sha-<previous-commit-sha>

# 查看状态和日志
sudo ./deploy.sh status
sudo ./deploy.sh logs --tail=100
sudo ./deploy.sh logs -f backend

# 停止服务但保留数据库 volume
sudo ./deploy.sh down
```

`install`、`update` 和 `rollback` 会在容器启动后自动刷新 nginx，镜像更新后通常不需要手动执行 `docker compose restart nginx`。

完整部署、备份、回滚和安全说明见 `docs/11_DEPLOYMENT_GUIDE.md`。

## 数据库与演示账号

本项目使用 Flyway 自动初始化数据库，迁移脚本位于：

```text
backend/src/main/resources/db/migration/
```

Flyway 只初始化 schema、默认管理员、默认普通用户和默认乘机人。航司、机场、航班、座位、订单、退票、改签、候补和 AI 演示数据使用可复现 seed 脚本按需生成：

```bash
# 生成并校验开发数据
python3 scripts/generate_test_data.py --profile dev --seed 20260707
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql

# 生成并校验测试数据
python3 scripts/generate_test_data.py --profile test --seed 20260707
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-test.sql

# Docker Compose MySQL 导入示例
docker exec -i skybooker-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-flight_booking}"' \
  < backend/src/main/resources/db/seed/seed-dev.sql
```

完整说明见 `docs/17_TEST_DATA_GUIDE.md`。

默认演示账号：

```text
普通用户：user1@example.com / User@123456
管理员用户名：admin
管理员密码：SkyBooker@Init2026!
管理员资料邮箱：admin@skybooker.local
```

首次部署后应修改默认管理员密码。演示日期过期时，请重新运行 `scripts/generate_test_data.py --base-date <YYYY-MM-DD>` 生成新的 seed SQL，再导入数据库。

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

前端构建：

```bash
cd frontend
pnpm build
```

部署烟测：

```bash
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
```

同座位并发下单验证：

```text
scripts/jmeter/same-seat-order-race.jmx
scripts/jmeter/run-same-seat-concurrency-report.sh
```
