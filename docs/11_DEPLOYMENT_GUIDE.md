# 11_DEPLOYMENT_GUIDE：部署指南

## 1. 本地开发环境

需要安装：

- JDK 17+
- Maven 3.8+
- Node.js 20+
- pnpm
- Docker Desktop
- MySQL 8
- Redis 7

## 2. 本地开发启动

### 启动 MySQL 和 Redis

```bash
cp .env.example .env
docker compose up -d mysql redis
```

### 启动后端

后端通过 `spring-dotenv` 自动加载项目根目录的 `.env`，无需手动 source。

```bash
cd backend
set -a; source ../.env; set +a
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

### 启动前端

当前 `frontend/` 目录只有 `DESIGN.md`，没有 Next.js 工程或构建输入。本分支不提供前端启动命令和前端容器。

前端工程补齐后，预期本地启动方式为：

```bash
cd frontend
pnpm install
pnpm dev
```

前端默认地址：

```text
http://localhost:3000
```

## 3. 环境变量

### 前端

`.env.local`：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
```

### 后端

后端通过 `spring-dotenv` 在启动时自动加载项目根目录 `.env`，不需要手动设置环境变量。`.env` 文件不提交到 Git，从 `.env.example` 复制生成。

```env
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DB=flight_booking
MYSQL_USER=root
MYSQL_PASSWORD=replace-with-local-password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=replace-with-a-random-secret-at-least-256-bits-long
JWT_ACCESS_MS=3600000
JWT_REFRESH_MS=1209600000
OPENAPI_ENABLED=false
AI_LLM_ENABLED=false
AI_LLM_BASE_URL=https://api.openai.com/v1
AI_LLM_API_KEY=replace-with-llm-provider-key
AI_LLM_MODEL=gpt-4o-mini
AI_LLM_TIMEOUT_MS=8000
AI_LLM_MAX_RETRIES=1
# 后台管理 LLM apiKey 的 AES-256 加密密钥（base64 32 字节，openssl rand -base64 32）。缺失则后台无法写入配置。
AI_CONFIG_ENC_KEY=
# 允许跨域的前端 Origin，逗号分隔。开发默认 localhost:3000；生产例如 https://skybooker.yunluostar.com
CORS_ALLOWED_ORIGINS=http://localhost:3000
BACKEND_PORT=8080
NGINX_PORT=8088
```

`MYSQL_PASSWORD` 和 `JWT_SECRET` 没有应用内默认值，部署环境必须显式提供。Swagger / Knife4j 文档默认关闭，需要在开发或测试环境将 `OPENAPI_ENABLED=true` 后才开放。

AI 助手默认使用规则解析，不需要 LLM 密钥。启用 LLM 意图解析时，将 `AI_LLM_ENABLED=true`，并配置兼容 OpenAI Chat Completions 的 `AI_LLM_BASE_URL`、`AI_LLM_API_KEY` 和 `AI_LLM_MODEL`。旧变量名 `AI_API_KEY` 不再使用，避免和 LLM 专用配置混淆。`AI_LLM_API_KEY` 只能写在本地 `.env` 或服务器环境变量中，不得提交真实值。

上述环境变量是 **fallback 默认值**：管理员还可通过 `GET / PUT /api/admin/ai/llm-config`（仅 ADMIN portal）在运行时查看（脱敏）和修改 provider 配置，数据库 `ai_llm_config` 记录优先于环境变量，写入后下一个 AI 请求即生效，无需重启。后台写入的 apiKey 加密入库需要独立的 `AI_CONFIG_ENC_KEY`（base64 编码 32 字节，`openssl rand -base64 32` 生成）：

- 该密钥缺失或格式非法时，应用仍可正常启动并走环境变量 fallback；仅当管理员尝试写入后台配置时返回 `AI_LLM_CONFIG_INVALID(10022)`，不落库。
- **务必妥善备份**：丢失 `AI_CONFIG_ENC_KEY` 后已加密入库的 apiKey 无法解密，系统会自动 fallback 环境变量默认值。该密钥不复用 `JWT_SECRET`，职责隔离。

## 4. Docker Compose 部署

当前 Compose 部署服务：

```text
mysql
redis
backend
nginx
```

说明：

- `backend` 使用 `backend/Dockerfile` 构建 Spring Boot API；
- `nginx` 使用 `deploy/nginx/api-gateway.conf` 转发 `/api` 和 `/api/` 到后端；
- 当前 `frontend/` 只有 `DESIGN.md`，没有 Next.js 工程或构建输入，因此本分支不提供真实前端容器；
- 未来前端工程补齐后，再新增 `frontend` 服务并由 Nginx 将 `/` 转发到前端服务。

`.env` 必须显式配置：

```env
MYSQL_PASSWORD=replace-with-local-password
JWT_SECRET=replace-with-a-random-secret-at-least-256-bits-long
MYSQL_DB=flight_booking
MYSQL_USER=root
BACKEND_PORT=8080
NGINX_PORT=8088
OPENAPI_ENABLED=false
AI_LLM_ENABLED=false
AI_LLM_BASE_URL=https://api.openai.com/v1
AI_LLM_API_KEY=replace-with-llm-provider-key
AI_LLM_MODEL=gpt-4o-mini
AI_LLM_TIMEOUT_MS=8000
AI_LLM_MAX_RETRIES=1
AI_CONFIG_ENC_KEY=
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

`docker-compose.yml` 不再为 MySQL 密码和 JWT 密钥提供 `123456` 这类明文默认值。缺少 `MYSQL_PASSWORD` 或 `JWT_SECRET` 时，Compose 会直接报错，避免误用不安全配置。

启动：

```bash
docker compose up -d --build
```

只启动基础设施：

```bash
docker compose up -d mysql redis
```

查看日志：

```bash
docker compose logs -f backend
```

健康检查：

```bash
docker compose ps
curl http://localhost:${BACKEND_PORT:-8080}/api/flights?page=1\&size=1
curl http://localhost:${NGINX_PORT:-8088}/healthz
curl http://localhost:${NGINX_PORT:-8088}/api/flights?page=1\&size=1
```

对外演示时优先访问 Nginx：

```text
API 基地址：http://localhost:8088/api
公开验证接口：http://localhost:8088/api/flights?page=1&size=1
```

## 5. Flyway 初始化

后端启动时会自动执行：

```text
backend/src/main/resources/db/migration/
```

如果数据库已存在旧表，开发阶段可以重建数据库：

```sql
DROP DATABASE IF EXISTS flight_booking;
CREATE DATABASE flight_booking DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后重启后端。

## 6. Nginx 反向代理示例

本仓库已提供 Compose 场景下的 API 网关配置：

```text
deploy/nginx/api-gateway.conf
```

其行为：

- `GET /healthz` 返回 Nginx 健康状态；
- `/api` 和 `/api/` 反向代理到 `backend:8080`；
- `/` 返回一个纯文本说明，提示当前分支未打包前端。

服务器部署时可参考下面的前后端分离示例。当前仓库还没有可构建的前端工程，因此 `/` 的前端代理需要等前端服务补齐后再启用。

```nginx
server {
    listen 80;
    server_name example.com;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 7. 演示环境建议

实训演示时推荐：

- 使用 Docker Compose 启动 MySQL、Redis、backend 和 Nginx；
- 如需展示前端页面，由前端同学按未来 Next.js 工程手动启动或接入；
- 提前准备演示数据；
- 如演示日期已晚于初始化航班日期，先执行 `scripts/refresh-demo-flight-dates.sql` 刷新演示航班和订单日期；
- 运行 `scripts/smoke/backend-smoke.sh` 验证后端、登录边界、AI 和管理员统计；
- 首次部署后修改默认管理员密码；
- 提前录制系统视频，避免现场网络或环境问题。

默认演示账号：

```text
管理员资料邮箱：admin@skybooker.local
普通用户：user1@example.com / User@123456
```

管理后台登录使用管理员用户名：

```text
后台入口：/admin
管理员用户名：admin
管理员密码：SkyBooker@Init2026!
```

## 8. 常见问题

### Flyway 报错脚本校验失败

原因：已经执行过的脚本被修改。

解决：开发阶段可以重建数据库；正式阶段不要修改已执行的 V 脚本，应新增 V 脚本。

### 前端请求后端跨域失败

后端已通过 `SecurityConfig` 启用 CORS，允许的 Origin 由环境变量 `CORS_ALLOWED_ORIGINS`（配置项 `app.cors.allowed-origins`）控制，逗号分隔。开发默认 `http://localhost:3000`；生产需设置为实际前端域名：

```text
CORS_ALLOWED_ORIGINS=https://skybooker.yunluostar.com
```

多 origin 用逗号分隔，例如 `CORS_ALLOWED_ORIGINS=http://localhost:3000,https://skybooker.yunluostar.com`。注意：因前端请求携带 `Authorization` 头，CORS 启用了 `allowCredentials`，Origin 列表必须是精确地址，**不能用通配 `*`**。修改后需重启后端生效。

### Token 失效

重新登录即可。

### Compose 提示缺少 MYSQL_PASSWORD 或 JWT_SECRET

原因：部署文件要求显式配置敏感变量。

解决：从 `.env.example` 复制 `.env`，填入本地 MySQL 密码和足够长的 JWT 密钥后重新启动。

### Docker Hub 镜像拉取失败

原因：本地网络或机房策略拒绝访问 Docker Hub。

解决：从团队批准的镜像源拉取等价的 `mysql:8.0`、`redis:7-alpine`、`maven:3.9-eclipse-temurin-21`、`eclipse-temurin:21-jre`、`nginx:1.27-alpine` 镜像，并打成本地标准标签后再运行 `docker compose up -d --build`。不要把镜像源凭据或个人代理配置提交到仓库。

### Nginx 首页不是前端页面

原因：当前 `frontend/` 目录没有 Next.js 工程或构建输入，本分支只提供 API 网关。

解决：访问 `/api/flights?page=1&size=1` 验证后端；前端工程补齐后，再新增前端容器或把 Nginx `/` 转发到前端服务。

## 邮件服务配置

后端通过 `MailService` 抽象发送注册和找回密码验证码。默认 `MAIL_PROVIDER=log`，验证码输出到后端日志，适合本地开发、演示和自动化测试；生产环境可显式切换到 Resend HTTP API。

### 后端环境变量

```env
MAIL_PROVIDER=log
MAIL_FROM=SkyBooker <noreply@your-domain.com>
RESEND_API_KEY=<your-resend-api-key>
RESEND_BASE_URL=https://api.resend.com
```

`MAIL_PROVIDER=log` 或未设置时不需要 `RESEND_API_KEY`。启用真实邮件发送时设置：

```env
MAIL_PROVIDER=resend
MAIL_FROM=SkyBooker <noreply@your-domain.com>
RESEND_API_KEY=<your-resend-api-key>
RESEND_BASE_URL=https://api.resend.com
```

Resend 要求 API key 可用，并且 `MAIL_FROM` 使用已验证发送域名或已获批准的发件地址。`MAIL_PROVIDER=resend` 时，如果 `MAIL_FROM` 或 `RESEND_API_KEY` 为空，后端会在启动创建邮件 bean 时清晰失败，不会静默回退到日志模式。

### Docker Compose 变量传递

`docker-compose.yml` 将邮件变量透传给 backend，并保持本地默认可启动：

```yaml
MAIL_PROVIDER: ${MAIL_PROVIDER:-log}
MAIL_FROM: ${MAIL_FROM:-}
RESEND_API_KEY: ${RESEND_API_KEY:-}
RESEND_BASE_URL: ${RESEND_BASE_URL:-https://api.resend.com}
```

注意不要使用 `${RESEND_API_KEY:?...}` 这类强制校验形式。凭据校验由后端在 `MAIL_PROVIDER=resend` 时完成，默认 log 模式不应阻止 `docker compose up`。

### 日志模式和回滚

日志模式下查看验证码：

```bash
docker compose logs -f backend | grep -i "verification\|验证码"
```

从 Resend 回滚到日志模式时，将 `.env` 中 `MAIL_PROVIDER` 改为 `log` 或删除该变量，然后重启后端：

```bash
docker compose up -d backend
```

短信验证码、微信登录、支付宝登录不作为默认部署依赖。SMTP、Brevo 和托管邮件模板可作为后续扩展。

## 9. 服务器部署

### 9.1 服务器前提条件

#### 操作系统

推荐使用以下 64 位 Linux 发行版：

- Ubuntu 22.04 LTS / 24.04 LTS
- Debian 12 (Bookworm)
- CentOS Stream 9 / Rocky Linux 9

其他能运行 Docker 的 Linux 发行版也可以使用，但以下命令以 `apt` 为例。

#### Docker 和 Docker Compose

```bash
# 安装 Docker Engine（Ubuntu 示例）
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 验证
docker --version
docker compose version
```

Docker Compose 版本需要支持 `docker compose`（V2 插件），不需要单独安装 `docker-compose`。

#### 资源预期

| 资源 | 最低要求 | 推荐配置 |
|------|----------|----------|
| CPU | 2 核 | 2 核 |
| 内存 | 4 GB | 4 GB |
| 磁盘 | 20 GB | 40 GB（含数据库和备份空间） |

MySQL 8 容器通常占用 1-2 GB 内存，Spring Boot 后端约 512 MB-1 GB，Redis 和 Nginx 占用较小。

#### 域名和 DNS（可选）

如果需要通过域名和 HTTPS 访问：

- 准备一个域名并将 A 记录指向服务器公网 IP；
- DNS 生效后再配置 HTTPS 证书。

纯 IP 访问不需要域名和 DNS 配置。

#### 防火墙和端口

| 端口 | 协议 | 用途 | 对外开放 |
|------|------|------|----------|
| 80 | TCP | HTTP / Certbot 验证 | 是（HTTPS 前置） |
| 443 | TCP | HTTPS | 是（生产环境推荐） |
| 8088 | TCP | HTTP API 网关（无域名时） | 按需 |
| 22 | TCP | SSH 管理 | 是 |

MySQL（3306）、Redis（6379）、后端（8080）默认绑定到 `127.0.0.1`，不对外开放。Nginx（8088）绑定到 `0.0.0.0`，是唯一的公共入口。

MySQL（3306）、Redis（6379）、后端（8080）端口不需要对外开放，只通过 Nginx 反向代理访问。

```bash
# UFW 示例
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8088/tcp   # 仅在无 Nginx 80/443 时需要
sudo ufw enable
```

云服务器还需在安全组中放行对应端口。

#### 仓库检出

```bash
# 安装 Git
sudo apt-get install -y git

# 克隆仓库
git clone https://github.com/<your-org>/skybooker-flight.git
cd skybooker-flight

# 切换到目标分支
git checkout main   # 或本次待部署的短生命周期分支
```

将 `<your-org>` 替换为实际的 GitHub 用户名或组织。

### 9.2 首次部署流程

#### 第一步：配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，填写所有占位值：

```env
MYSQL_PASSWORD=<strong-random-password>
MYSQL_DB=flight_booking
MYSQL_USER=root
JWT_SECRET=<random-secret-at-least-256-bits>
BACKEND_PORT=127.0.0.1:8080
NGINX_PORT=8088
OPENAPI_ENABLED=false
AI_LLM_ENABLED=false
```

生成随机密码和 JWT 密钥的示例：

```bash
openssl rand -base64 32   # MySQL 密码
openssl rand -base64 48   # JWT 密钥
```

#### 第二步：构建并启动服务

```bash
docker compose up -d --build
```

首次构建需要下载基础镜像和 Maven 依赖，可能需要 5-10 分钟。

#### 第三步：检查服务状态

```bash
docker compose ps
```

确认所有服务状态为 `healthy` 或 `running`：

```text
skybooker-mysql    ... Up (healthy)
skybooker-redis    ... Up (healthy)
skybooker-backend  ... Up (healthy)
skybooker-nginx    ... Up (healthy)
```

#### 第四步：验证 API 可达

```bash
# 通过 Nginx API 网关验证
curl http://localhost:8088/healthz
# 期望输出: ok

curl http://localhost:8088/api/flights?page=1\&size=1
# 期望返回标准 ApiResponse JSON
```

如果通过公网 IP 访问，将 `localhost` 替换为服务器公网 IP。

#### 第五步：首次 Smoke 验证（使用默认密码）

在修改管理员密码之前，先用默认凭据跑一次 smoke 确认部署正常：

```bash
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
```

所有检查通过后，再修改管理员密码。

#### 第六步：修改默认管理员密码

使用默认管理员账号登录并通过管理后台修改密码：

```text
管理员用户名：admin
默认密码：SkyBooker@Init2026!
```

修改密码后，后续 smoke 验证必须显式传入新密码：

```bash
SKYBOOKER_ADMIN_PASSWORD='<new-admin-password>' \
SKYBOOKER_BASE_URL=http://localhost:8088 \
scripts/smoke/backend-smoke.sh
```

### 9.3 当前部署边界和前端集成路径

当前仓库部署以下服务：

- **MySQL 8**：关系数据库，Flyway 自动初始化表结构和种子数据；
- **Redis 7**：缓存和会话存储；
- **Spring Boot 后端**：REST API，监听容器内 8080 端口；
- **Nginx API 网关**：反向代理，转发 `/api/` 到后端，提供 `/healthz` 健康检查。

当前 `frontend/` 目录只有 `DESIGN.md`，没有 Next.js 工程或构建输入。本仓库不打包前端容器。

前端工程补齐后，预期集成方式：

1. 在 `docker-compose.yml` 中新增 `frontend` 服务；
2. 在 Nginx 配置中将 `/` 路由到前端服务；
3. 前端通过 `NEXT_PUBLIC_API_BASE_URL` 指向 Nginx 的 `/api` 路径。

### 9.4 演示数据刷新

Flyway 种子数据中的航班日期是固定的。部署后如果航班日期已过期，需要手动刷新。

以下命令需要读取 `.env` 中的 `MYSQL_PASSWORD`。执行前先加载环境变量：

```bash
set -a; source ./.env; set +a
```

然后执行刷新：

```bash
# 通过 Docker 执行 SQL
docker exec -i skybooker-mysql mysql -uroot -p"$MYSQL_PASSWORD" flight_booking \
  < scripts/refresh-demo-flight-dates.sql
```

或者从宿主机执行（需要本地 mysql 客户端）：

```bash
mysql -h 127.0.0.1 -P 3306 -uroot -p"$MYSQL_PASSWORD" flight_booking \
  < scripts/refresh-demo-flight-dates.sql
```

建议在以下时机执行刷新：

- 首次部署后验证数据前；
- 每次演示前一天；
- 升级后如果涉及种子数据变更。

## 10. 服务器运行配置和安全

### 10.1 服务器环境变量参考

服务器 `.env` 必须包含以下值，所有敏感值使用占位符：

```env
# === 必填 ===
MYSQL_PASSWORD=<strong-random-password>
MYSQL_DB=flight_booking
MYSQL_USER=root
JWT_SECRET=<random-secret-at-least-256-bits>

# === 网络 ===
# 端口变量支持 127.0.0.1:PORT 格式（仅本地访问）或纯 PORT 格式（所有接口）
# MySQL、Redis、Backend 默认绑定 127.0.0.1，Nginx 默认绑定 0.0.0.0
BACKEND_PORT=127.0.0.1:8080
NGINX_PORT=8088

# === 功能开关 ===
OPENAPI_ENABLED=false

# === AI 助手（可选） ===
AI_LLM_ENABLED=false
AI_LLM_BASE_URL=https://api.openai.com/v1
AI_LLM_API_KEY=<your-llm-provider-key>
AI_LLM_MODEL=gpt-4o-mini
AI_LLM_TIMEOUT_MS=8000
AI_LLM_MAX_RETRIES=1

# === 邮件服务 ===
MAIL_PROVIDER=log
MAIL_FROM=SkyBooker <noreply@your-domain.com>
RESEND_API_KEY=<your-resend-api-key>
RESEND_BASE_URL=https://api.resend.com

# === Redis（Compose 内部通常不需要修改） ===
REDIS_HOST=redis
REDIS_PORT=6379
```

`MYSQL_HOST`、`MYSQL_PORT`、`REDIS_HOST`、`REDIS_PORT` 在 Compose 部署中由 `docker-compose.yml` 自动注入到后端容器，不需要在 `.env` 中设置。

**端口绑定安全**：`docker-compose.yml` 默认将 MySQL、Redis、Backend 端口绑定到 `127.0.0.1`，不对外暴露。如果在 `.env` 中设置 `MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT` 为纯数字（如 `MYSQL_PORT=3306`），会覆盖默认行为，改为绑定到所有网络接口（`0.0.0.0`）。公网服务器部署时：

- 如果不需要从宿主机直接访问这些服务，**删除或注释** `.env` 中的 `MYSQL_PORT`、`REDIS_PORT`、`BACKEND_PORT` 行，让 Compose 使用默认的 `127.0.0.1` 绑定；
- 如果需要本地访问（如手动执行 SQL），保持默认的 `127.0.0.1:PORT` 格式，例如：

```env
MYSQL_PORT=127.0.0.1:3306
REDIS_PORT=127.0.0.1:6379
BACKEND_PORT=127.0.0.1:8080
```

### 10.2 生产安全检查清单

部署到公网前，逐项确认：

- [ ] `JWT_SECRET` 已替换为足够长的随机密钥（至少 256 位，使用 `openssl rand -base64 48` 生成）；
- [ ] `MYSQL_PASSWORD` 已替换为强密码，不使用 `123456` 或默认值；
- [ ] 默认管理员密码 `SkyBooker@Init2026!` 已通过管理后台修改；
- [ ] `OPENAPI_ENABLED=false`（Swagger/Knife4j 不对外暴露）；
- [ ] 对外只开放必要端口（80/443 或 8088）；
- [ ] MySQL（3306）、Redis（6379）、后端（8080）端口绑定到 `127.0.0.1`（Compose 默认行为）；
- [ ] `.env` 文件在 `.gitignore` 中，不会被提交；
- [ ] 没有真实密钥、密码、证书或 IP 出现在仓库文件中。

### 10.3 敏感信息审查

确认仓库中提交的文档和配置文件不包含以下内容：

- 真实服务器 IP 地址或内网域名；
- 数据库密码、JWT 密钥、邮件凭据、LLM API 密钥；
- SSL/TLS 证书私钥文件或签发证书文件；
- 本地 `.env` 文件；
- 运行日志、报告输出、截图。

所有示例值使用 `<placeholder>` 格式（如 `<strong-random-password>`、`<your-llm-provider-key>`），不含真实数据。

## 11. 生产反向代理和 HTTPS

### 11.1 域名方式 API 路由

当服务器有域名时，使用独立的 Nginx 配置文件将公网请求代理到 Compose 的 Nginx 容器。

以下示例假设 Compose 的 `nginx` 服务在宿主机上映射到 8088 端口（`NGINX_PORT=8088`），服务器上的独立 Nginx 监听公网 80/443 端口并代理到 `127.0.0.1:8088`。

```nginx
# /etc/nginx/sites-available/skybooker.conf
server {
    listen 80;
    server_name skybooker.example.com;

    location /healthz {
        proxy_pass http://127.0.0.1:8088/healthz;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /api {
        proxy_pass http://127.0.0.1:8088/api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8088/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 前端工程补齐后取消注释
    # location / {
    #     proxy_pass http://127.0.0.1:3000/;
    #     proxy_set_header Host $host;
    #     proxy_set_header X-Real-IP $remote_addr;
    # }
}
```

将 `skybooker.example.com` 替换为实际域名。启用配置：

```bash
sudo ln -s /etc/nginx/sites-available/skybooker.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

验证：

```bash
curl http://skybooker.example.com/healthz
curl http://skybooker.example.com/api/flights?page=1\&size=1
```

### 11.2 HTTPS 配置

推荐使用 Certbot 自动获取 Let's Encrypt 证书：

```bash
# 安装 Certbot
sudo apt-get install -y certbot python3-certbot-nginx

# 获取证书（确保域名 DNS 已指向服务器且 Nginx 正在运行）
sudo certbot --nginx -d skybooker.example.com
```

Certbot 会自动修改 Nginx 配置并设置自动续期。

**注意事项：**

- 证书私钥和签发证书文件保存在 `/etc/letsencrypt/` 下，属于服务器本地文件，不要复制到仓库；
- 使用 `--nginx` 参数时 Certbot 会自动处理 HTTP 到 HTTPS 的重定向；
- 续期测试：`sudo certbot renew --dry-run`。

使用云服务商托管证书时，按照服务商文档配置 SSL/TLS 监听器，证书文件同样不应提交到仓库。

### 11.3 纯 IP 的 HTTP 验证路径

在域名和 HTTPS 就绪前，可以通过服务器 IP 和 Compose 暴露的端口验证部署：

```bash
# 确保 8088 端口对外开放
curl http://<server-ip>:8088/healthz
curl http://<server-ip>:8088/api/flights?page=1\&size=1
```

这是部署初期的临时验证方式。生产环境建议通过域名和 HTTPS 访问。

### 11.4 可复用 Nginx 模板

仓库提供了生产 Nginx 模板：

```text
deploy/nginx/server-block.conf.example
```

该模板包含域名占位符和 HTTPS 注释，不包含任何环境特定值。使用时复制到服务器并替换占位符。

## 12. 服务器验证和证据采集

### 12.1 服务器 Smoke 验证

通过 Nginx 公网路由运行 smoke 脚本：

```bash
# 纯 IP 验证
SKYBOOKER_BASE_URL=http://<server-ip>:8088 scripts/smoke/backend-smoke.sh

# 域名验证（HTTP）
SKYBOOKER_BASE_URL=http://skybooker.example.com scripts/smoke/backend-smoke.sh

# 域名验证（HTTPS）
SKYBOOKER_BASE_URL=https://skybooker.example.com scripts/smoke/backend-smoke.sh
```

首次部署时使用默认管理员密码（`SkyBooker@Init2026!`），smoke 可直接运行。修改管理员密码后，必须显式传入新密码：

```bash
SKYBOOKER_ADMIN_PASSWORD='<new-admin-password>' \
SKYBOOKER_BASE_URL=https://skybooker.example.com \
scripts/smoke/backend-smoke.sh
```

### 12.2 验证覆盖范围

Smoke 脚本覆盖以下检查：

| 检查项 | 端点 | 说明 |
|--------|------|------|
| 公共航班查询 | `GET /api/flights` | 无需登录，验证 Flyway 数据 |
| 用户登录 | `POST /api/auth/login` | 普通用户邮箱密码登录 |
| 用户身份 | `GET /api/auth/me` | 验证用户 Token 有效性 |
| 管理员登录 | `POST /api/admin/auth/login` | 管理员用户名密码登录 |
| 管理员身份 | `GET /api/admin/me` | 验证管理员 Token 有效性 |
| 用户/管理员隔离 | 交叉访问 | 用户不能访问管理员接口，反之亦然 |
| 用户订单 | `GET /api/orders` | 登录后查看订单列表 |
| AI 助手 | `POST /api/ai/chat` | 匿名 AI 聊天响应 |
| 管理员数据统计 | `GET /api/admin/dashboard/summary` | 管理后台数据摘要 |

高级报表验证（`/api/admin/reports/**`）仅在报表端点已启用时覆盖。Smoke 脚本不将高级报表作为必要检查项，以避免对可选功能产生硬依赖。如需单独验证高级报表，可手动调用：

```bash
# 获取管理员 Token 后手动验证
ADMIN_TOKEN=<token>
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$SKYBOOKER_BASE_URL/api/admin/reports/sales-trend?startDate=2025-01-01&endDate=2026-12-31&granularity=MONTH" \
  | head -c 500
```

### 12.3 证据输出

Smoke 脚本输出保存在：

```text
reports/smoke/
```

该目录默认不提交到 Git（已包含在 `.gitignore` 中）。

PR 或演示中需要提交的证据：

- 关键 smoke 命令及其返回结果摘要；
- `docker compose ps` 输出（所有服务 healthy）；
- `/healthz` 和 `/api/flights` 的 curl 输出；
- 如有前端页面，浏览器截图。

### 12.4 展示就绪检查

服务器部署用于展示前，额外确认：

- [ ] 所有服务 healthy（`docker compose ps`）；
- [ ] Smoke 脚本通过（`scripts/smoke/backend-smoke.sh`）；
- [ ] 演示数据日期未过期（`scripts/refresh-demo-flight-dates.sql`）；
- [ ] 管理员密码已从默认值修改；
- [ ] 前端可通过 Nginx 访问或由前端同学另行启动；
- [ ] 服务器防火墙和安全组配置正确。

## 13. 运维、升级、回滚和备份

### 13.1 服务状态和日志

```bash
# 查看所有服务状态
docker compose ps

# 查看后端日志
docker compose logs -f backend

# 查看 Nginx 日志
docker compose logs -f nginx

# 查看 MySQL 日志
docker compose logs -f mysql

# 查看全部日志（最近 100 行）
docker compose logs --tail=100

# 重启单个服务
docker compose restart backend

# 重启全部服务
docker compose restart

# 完全重建并重启
docker compose up -d --build --force-recreate
```

### 13.2 升级流程

```bash
# 0. 加载环境变量（备份和恢复需要 MYSQL_PASSWORD）
set -a; source ./.env; set +a

# 1. 备份数据库（必须）
docker exec skybooker-mysql mysqldump -uroot -p"$MYSQL_PASSWORD" flight_booking \
  | gzip > /tmp/skybooker-backup-$(date +%Y%m%d%H%M%S).sql.gz

# 2. 记录当前版本
git log --oneline -1

# 3. 拉取最新代码
git pull origin main   # 或目标分支

# 4. 重建并重启
docker compose up -d --build

# 5. 等待服务 healthy
docker compose ps

# 6. 运行 Smoke 验证
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh

# 7. 如有新的 Flyway 迁移，后端启动时自动执行
#    确认迁移成功：
docker compose logs backend | grep -i flyway
```

升级失败时，参照 13.3 回滚流程操作。

### 13.3 回滚流程

当升级后 Smoke 验证失败时：

```bash
# 0. 加载环境变量（恢复需要 MYSQL_PASSWORD）
set -a; source ./.env; set +a

# 1. 回退到上一个已知正常版本
git checkout <previous-working-commit>

# 2. 重建并重启
docker compose up -d --build

# 3. 等待服务 healthy
docker compose ps

# 4. 如果数据库已变更导致不兼容，恢复备份
docker exec -i skybooker-mysql mysql -uroot -p"$MYSQL_PASSWORD" flight_booking \
  < <(gunzip -c /tmp/skybooker-backup-<timestamp>.sql.gz)

# 5. 重启后端并验证
docker compose restart backend
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
```

### 13.4 数据库备份和恢复

#### 手动备份

```bash
# 加载环境变量
set -a; source ./.env; set +a

# 备份到 /tmp 目录
docker exec skybooker-mysql mysqldump -uroot -p"$MYSQL_PASSWORD" flight_booking \
  | gzip > /tmp/skybooker-backup-$(date +%Y%m%d%H%M%S).sql.gz

# 查看备份文件
ls -lh /tmp/skybooker-backup-*.sql.gz
```

#### 手动恢复

```bash
# 加载环境变量
set -a; source ./.env; set +a

# 从备份文件恢复
docker exec -i skybooker-mysql mysql -uroot -p"$MYSQL_PASSWORD" flight_booking \
  < <(gunzip -c /tmp/skybooker-backup-<timestamp>.sql.gz)
```

备份文件默认保存在 `/tmp/` 目录，不提交到 Git。如需持久保存，将备份文件移到服务器上的专用备份目录（如 `/opt/skybooker/backups/`），并确保该目录在 `.gitignore` 中。

#### 定时备份（可选）

```bash
# 添加 crontab 每天凌晨 3 点自动备份
crontab -e
# 添加以下行：
# 0 3 * * * docker exec skybooker-mysql mysqldump -uroot -p<password> flight_booking | gzip > /opt/skybooker/backups/skybooker-backup-$(date +\%Y\%m\%d\%H\%M\%S).sql.gz
```

注意：crontab 中需要使用实际密码或环境变量，不要在仓库中记录真实密码。
