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
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

### 启动前端

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
JWT_EXPIRATION=86400000
OPENAPI_ENABLED=false
AI_API_KEY=optional
```

`MYSQL_PASSWORD` 和 `JWT_SECRET` 没有应用内默认值，部署环境必须显式提供。Swagger / Knife4j 文档默认关闭，需要在开发或测试环境将 `OPENAPI_ENABLED=true` 后才开放。

## 4. Docker Compose 部署

建议服务：

```text
mysql
redis
backend
frontend
nginx
```

启动：

```bash
docker compose up -d --build
```

查看日志：

```bash
docker compose logs -f backend
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

- 使用 Docker Compose 启动 MySQL 和 Redis；
- 本地运行后端；
- 本地运行前端；
- 提前准备演示数据；
- 如演示日期已晚于初始化航班日期，先执行 `scripts/refresh-demo-flight-dates.sql` 刷新演示航班和订单日期；
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
管理员密码：Admin@123456
```

## 8. 常见问题

### Flyway 报错脚本校验失败

原因：已经执行过的脚本被修改。

解决：开发阶段可以重建数据库；正式阶段不要修改已执行的 V 脚本，应新增 V 脚本。

### 前端请求后端跨域失败

后端配置 CORS，允许：

```text
http://localhost:3000
```

### Token 失效

重新登录即可。

## 邮件服务配置

SkyBooker 注册和找回密码需要发送邮箱验证码。开发环境可以使用 Mock 邮件服务，部署环境可以使用 SMTP 邮件服务。

### 后端环境变量

```env
MAIL_ENABLED=true
MAIL_PROVIDER=smtp
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your-email@example.com
MAIL_PASSWORD=your-email-auth-code
MAIL_FROM=SkyBooker <your-email@example.com>
```

### Spring Mail 配置示例

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.example.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### 开发环境 Mock 邮件

如果没有邮件服务账号，可以启用 Mock 模式：

```env
MAIL_ENABLED=false
MAIL_PROVIDER=mock
```

Mock 模式下后端不发送真实邮件，而是在控制台日志打印验证码，方便本地开发和课堂演示。

### 推荐邮件服务

- 个人邮箱 SMTP：适合本地演示；
- Brevo：适合免费事务邮件发送；
- Resend：适合开发者 API 邮件发送。

短信验证码、微信登录、支付宝登录不作为默认部署依赖。
