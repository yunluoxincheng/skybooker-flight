# 11_DEPLOYMENT_GUIDE：部署指南

SkyBooker 支持两条 Docker 路径：

- 本地开发：仓库根目录 `docker-compose.yml`，可本地构建 backend，并启动 MySQL、Redis、backend、nginx。该 nginx 仅作 API 网关（`/api/**`、`/healthz`），根路径返回引导文本、不提供前端 UI；前端请用 `cd frontend && pnpm dev` 热重载运行。要看完整 UI 或验收部署，使用下面的生产路径。
- 生产部署：`scripts/deploy.sh` 下载仓库维护的 `deploy/docker-compose.prod.yml` 和 `deploy/nginx/prod.conf`，在服务器上拉取已发布镜像并启动 all-in-one 栈。

当前第一版生产部署只支持 all-in-one 单机拓扑：MySQL、Redis、backend、frontend、nginx 在同一个 Docker Compose 项目中运行。使用外部 MySQL/Redis 的 app-only 部署是后续工作。

## 0. 最快启动（演示/测试）

如果你只是想在一台已经安装 Docker 的 Linux 服务器上最快跑起完整系统，直接执行：

```bash
curl -fsSL https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/main/scripts/deploy.sh \
  | sudo bash
```

这条命令适合课程演示、临时测试环境、个人服务器快速验证。它会自动完成这些事：

1. 创建部署目录 `/opt/skybooker`。
2. 下载生产 Compose 模板到 `/opt/skybooker/docker-compose.yml`。
3. 下载 nginx 配置到 `/opt/skybooker/nginx/default.conf`。
4. 保存一份部署脚本到 `/opt/skybooker/deploy.sh`。
5. 首次生成 `/opt/skybooker/.env`，并自动生成数据库密码、JWT 密钥和 AI 配置加密密钥。
6. 拉取后端、前端、MySQL、Redis、nginx 镜像。
7. 启动完整 all-in-one 服务栈。

启动完成后访问：

```text
http://<server-ip>:8088
```

在服务器上验证：

```bash
cd /opt/skybooker
sudo ./deploy.sh status
curl http://localhost:8088/healthz
curl 'http://localhost:8088/api/flights?page=1&size=1'
```

如果服务没有全部变成 healthy，先看后端日志：

```bash
cd /opt/skybooker
sudo ./deploy.sh logs --tail=200 backend
```

正式上线时不建议长期依赖 `main` 和 `latest`。推荐按第 4 节的“安全安装”方式固定 `DEPLOY_REF` 和镜像 tag，这样升级和回滚更可控。

## 1. 本地开发

需要安装：

- JDK 21+
- Maven 3.8+
- Node.js 24+
- pnpm 10+
- Docker Engine 或 Docker Desktop

启动基础设施：

```bash
cp .env.example .env
docker compose up -d mysql redis
```

启动后端：

```bash
cd backend
set -a; source ../.env; set +a
mvn spring-boot:run
```

启动前端：

```bash
cd frontend
pnpm install
pnpm dev
```

本地默认地址：

- 后端 API：`http://localhost:8080/api`
- 前端（`pnpm dev` 热重载）：`http://localhost:3000`
- 本地 nginx 网关（仅 `/api/**`、`/healthz`；根路径不含前端 UI）：`http://localhost:8088`

要看完整前端 UI 或验收部署效果，通过 `scripts/deploy.sh` 启动——它会基于 `deploy/docker-compose.prod.yml` 和 `deploy/nginx/prod.conf` 自动准备好生产 compose、nginx 配置和 `.env`（见第 0 节最快启动）；其 nginx 根路径会反代 frontend 容器。

## 2. 生产服务器前提条件

推荐 64 位 Linux 主机，例如 Ubuntu 22.04/24.04、Debian 12、Rocky Linux 9。最低建议 2 CPU、4 GB 内存、20 GB 磁盘；演示或长期运行建议至少 40 GB 磁盘并预留备份空间。

如果你用的是云服务器，还需要确认安全组或防火墙放行对外访问端口。默认只需要放行 `8088/tcp`；如果前面还有宿主机 nginx、负载均衡或 HTTPS 网关，则由它们监听 80/443，再转发到 `8088`。

服务器必须已安装：

- Docker Engine
- Docker Compose V2 插件，命令为 `docker compose`
- `curl`
- `openssl`

安装 Docker 的 Ubuntu 示例：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
docker --version
docker compose version
```

公网只需要开放 nginx 入口端口。默认 `PUBLIC_HTTP_PORT=8088`，生产域名场景可由宿主机 nginx 或云负载均衡监听 80/443 后代理到该端口。MySQL、Redis、backend、frontend 在生产 Compose 中不映射宿主机端口。

换句话说，外部访问路径是：

```text
浏览器/用户 -> 服务器 PUBLIC_HTTP_PORT -> nginx 容器 -> frontend/backend
```

MySQL、Redis、backend、frontend 只在 Docker 内部网络中互相通信，默认不会直接暴露到公网。

## 3. 镜像发布

GitHub Actions 工作流：

```text
.github/workflows/docker-images.yml
```

该工作流独立构建并推送两个镜像。默认推送到 GitHub Container Registry（GHCR）：

```text
ghcr.io/yunluoxincheng/skybooker-flight/skybooker-backend
ghcr.io/yunluoxincheng/skybooker-flight/skybooker-frontend
```

如果仓库配置了 Docker Hub 发布变量和密钥，同一批 tag 也会额外推送到 Docker Hub：

```text
docker.io/<dockerhub-namespace>/skybooker-backend
docker.io/<dockerhub-namespace>/skybooker-frontend
```

默认分支 `main` 发布：

- `latest`
- `sha-<commit-sha>`

Release tag（例如 `v1.2.3`）发布：

- `v1.2.3`
- `sha-<commit-sha>`

Pull request 只验证镜像构建，不推送镜像。工作流默认用 `GITHUB_TOKEN` 登录 GHCR，仓库需要允许 Actions 写入 packages。若 GHCR 镜像保持 private，部署服务器需要提前登录 GHCR：

```bash
echo '<github-token-with-read-packages>' | docker login ghcr.io -u '<github-user>' --password-stdin
```

Docker Hub 推送是可选的。要启用 Docker Hub，同仓库配置：

```text
Repository variable:
DOCKERHUB_NAMESPACE=<dockerhub-username-or-org>

Repository secrets:
DOCKERHUB_USERNAME=<dockerhub-username>
DOCKERHUB_TOKEN=<dockerhub-access-token>
```

如果只配置 `DOCKERHUB_NAMESPACE` 但缺少 username/token，推送任务会跳过 Docker Hub tag 并继续推 GHCR。

可通过工作流环境变量调整默认命名：

```yaml
GHCR_REGISTRY: ghcr.io
GHCR_IMAGE_NAMESPACE: ${{ github.repository }}
DOCKERHUB_REGISTRY: docker.io
DOCKERHUB_NAMESPACE: ${{ vars.DOCKERHUB_NAMESPACE }}
DEFAULT_DEPLOY_BRANCH: main
```

部署服务器默认使用 GHCR，也可在 `.env` 中覆盖到任意镜像源：

```env
BACKEND_IMAGE=ghcr.io/<owner>/<repo>/skybooker-backend
FRONTEND_IMAGE=ghcr.io/<owner>/<repo>/skybooker-frontend
IMAGE_TAG=latest
```

Docker Hub 示例：

```env
BACKEND_IMAGE=docker.io/<dockerhub-namespace>/skybooker-backend
FRONTEND_IMAGE=docker.io/<dockerhub-namespace>/skybooker-frontend
IMAGE_TAG=latest
```

## 4. 安全安装

默认安装目录：

```text
/opt/skybooker
```

生产环境不建议直接执行 `curl | sudo bash`。推荐先下载脚本，检查内容，再执行；上线时优先把 URL 中的 `main` 替换为已发布 tag 或 commit SHA。

这里有两个不同的版本概念：

- `DEPLOY_REF`：用来下载部署脚本、Compose 模板和 nginx 配置的 Git ref，可以是 `main`、tag 或 commit SHA。
- `IMAGE_TAG`：用来拉取 backend/frontend 镜像的 tag，例如 `latest`、`v1.0.0` 或 `sha-<commit-sha>`。

演示环境可以用 `main` + `latest`，生产环境建议固定两者，避免脚本模板和镜像在你不知情时变化。

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
less deploy.sh
chmod +x deploy.sh
sudo ./deploy.sh install --ref "$DEPLOY_REF" --tag sha-<commit-sha>
```

脚本会：

1. 创建 `/opt/skybooker` 和 `/opt/skybooker/nginx`。
2. 下载仓库维护的生产 Compose 与 nginx 模板。
3. 保存一份 `deploy.sh` 到部署目录。
4. 首次生成 `.env`，包括强随机 `MYSQL_PASSWORD`、`JWT_SECRET`、`AI_CONFIG_ENC_KEY`。
5. 拉取 backend/frontend 镜像和基础设施镜像。
6. 启动 MySQL、Redis、backend、frontend、nginx。

部署完成后，脚本落地的目录长这样：

```text
/opt/skybooker/
├── .env
├── deploy.sh
├── docker-compose.yml
└── nginx/
    └── default.conf
```

其中 `.env` 是服务器本地配置和密钥文件，不要提交到 Git，也不要贴到公开 issue、聊天记录或 PR 评论中。

指定不可变镜像 tag 首次部署：

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
chmod +x deploy.sh
sudo ./deploy.sh install --ref "$DEPLOY_REF" --tag sha-<commit-sha>
```

自定义目录、仓库、模板分支或镜像命名：

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
chmod +x deploy.sh
sudo ./deploy.sh install \
  --dir /srv/skybooker \
  --repo yunluoxincheng/skybooker-flight \
  --ref "$DEPLOY_REF" \
  --image-namespace ghcr.io/yunluoxincheng/skybooker-flight
```

使用 Docker Hub 镜像源：

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
chmod +x deploy.sh
sudo ./deploy.sh install \
  --ref "$DEPLOY_REF" \
  --image-source dockerhub \
  --dockerhub-namespace <dockerhub-namespace>
```

也可以直接指定完整镜像命名空间：

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
chmod +x deploy.sh
sudo ./deploy.sh install \
  --ref "$DEPLOY_REF" \
  --image-namespace docker.io/<dockerhub-namespace>
```

在 clean server 上也可以用环境变量传递同样的覆盖项：

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
chmod +x deploy.sh
sudo env SKYBOOKER_DEPLOY_DIR=/srv/skybooker \
  SKYBOOKER_REPO=yunluoxincheng/skybooker-flight \
  SKYBOOKER_REF="$DEPLOY_REF" \
  SKYBOOKER_IMAGE_NAMESPACE=ghcr.io/yunluoxincheng/skybooker-flight \
  ./deploy.sh install
```

Docker Hub 环境变量示例：

```bash
DEPLOY_REF=<tag-or-commit-sha>
curl -fsSLO "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh"
chmod +x deploy.sh
sudo env SKYBOOKER_IMAGE_SOURCE=dockerhub \
  SKYBOOKER_DOCKERHUB_NAMESPACE=<dockerhub-namespace> \
  SKYBOOKER_REF="$DEPLOY_REF" \
  ./deploy.sh install
```

## 5. 手动安装备选

需要手动下载全部部署资产时，可以执行：

```bash
DEPLOY_REF=<tag-or-commit-sha>
sudo install -d -m 0755 /opt/skybooker/nginx
cd /opt/skybooker
sudo curl -fsSL "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/deploy/docker-compose.prod.yml" \
  -o docker-compose.yml
sudo curl -fsSL "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/deploy/nginx/prod.conf" \
  -o nginx/default.conf
sudo curl -fsSL "https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/${DEPLOY_REF}/scripts/deploy.sh" \
  -o deploy.sh
sudo chmod +x deploy.sh
sudo ./deploy.sh install --ref "$DEPLOY_REF"
```

如果需要先只生成文件和 `.env`，不启动容器：

```bash
sudo ./deploy.sh install --prepare-only
```

## 6. 运维命令

所有命令默认作用于 `/opt/skybooker`：

```bash
cd /opt/skybooker

# 更新到当前 IMAGE_TAG，默认 latest
sudo ./deploy.sh update

# 更新到指定不可变 tag
sudo ./deploy.sh update --tag sha-<commit-sha>

# 切换到 Docker Hub 镜像源并更新 .env 中的 BACKEND_IMAGE/FRONTEND_IMAGE
sudo ./deploy.sh update \
  --image-source dockerhub \
  --dockerhub-namespace <dockerhub-namespace>

# 回滚到上一个已知正常 tag
sudo ./deploy.sh rollback --tag sha-<previous-commit-sha>

# 查看状态
sudo ./deploy.sh status

# 查看全部日志
sudo ./deploy.sh logs --tail=100

# 跟随后端日志
sudo ./deploy.sh logs -f backend

# 停止服务但保留数据库 volume
sudo ./deploy.sh down
```

`update` 和 `rollback` 都不会删除 `mysql_data` volume。数据库结构变更由 backend 启动时的 Flyway 自动执行；上线前仍应先备份数据库。显式传入 `--image-source`、`--dockerhub-namespace` 或 `--image-namespace` 时，脚本会同步更新 `.env` 中的 `BACKEND_IMAGE` 和 `FRONTEND_IMAGE`，用于在 GHCR 和 Docker Hub 等镜像源之间切换。

`install`、`update` 和 `rollback` 在 `docker compose up -d` 成功后会自动重启 nginx 容器，让 nginx 重新解析 backend/frontend upstream 并清理旧连接。镜像更新后通常不需要再手动执行 `docker compose restart nginx`。

常用排查命令：

```bash
cd /opt/skybooker

# 查看所有容器状态
sudo docker compose --env-file .env -f docker-compose.yml ps

# 查看后端最近日志
sudo ./deploy.sh logs --tail=200 backend

# 查看 MySQL / Redis 日志
sudo ./deploy.sh logs --tail=100 mysql
sudo ./deploy.sh logs --tail=100 redis

# 重新拉取镜像并启动
sudo ./deploy.sh update
```

## 7. 生产 `.env`

脚本首次安装会创建权限为 `600` 的 `.env`。重复运行时保留已有值，只追加缺失键；`MYSQL_PASSWORD=`、`JWT_SECRET=""`、`AI_CONFIG_ENC_KEY=''` 这类空值会被视为缺失并重新生成。

只有显式使用 `--regenerate-secrets` 时才会替换生成类 secret；该选项会导致已签发 token 失效，并且已有 `mysql_data` volume 时不会自动修改 MySQL 数据目录中已初始化的 root 密码。脚本检测到已有 MySQL volume 会拒绝执行 `--regenerate-secrets`，需要先备份数据并按数据库运维流程手动轮换凭据。

核心变量：

```env
COMPOSE_PROJECT_NAME=skybooker
BACKEND_IMAGE=ghcr.io/yunluoxincheng/skybooker-flight/skybooker-backend
FRONTEND_IMAGE=ghcr.io/yunluoxincheng/skybooker-flight/skybooker-frontend
IMAGE_TAG=latest
PUBLIC_HTTP_PORT=8088

MYSQL_DB=flight_booking
MYSQL_PASSWORD=<generated>
JWT_SECRET=<generated>
AI_CONFIG_ENC_KEY=<generated-base64-32-bytes>

OPENAPI_ENABLED=false
CORS_ALLOWED_ORIGINS=http://localhost:8088
MAIL_PROVIDER=log
RESEND_API_KEY=
AI_LLM_ENABLED=false
AI_LLM_API_KEY=
```

生产 all-in-one Compose 内置 MySQL 第一版固定使用 root 账号，`MYSQL_PASSWORD` 同时作为 MySQL root 密码和 backend 连接密码。不要在生产 `.env` 中配置非 root `MYSQL_USER`；外部数据库和独立应用用户属于后续 app-only 部署设计。

`AI_CONFIG_ENC_KEY` 必须妥善备份。丢失后，数据库中已加密保存的 AI provider key 无法解密，只能回退到环境变量配置或重新写入。

## 8. 生产路由

生产 Compose 使用 `deploy/nginx/prod.conf`：

- `GET /healthz` 直接返回 `ok`。
- `/api` 和 `/api/` 转发到 `backend:8080`。
- `/` 转发到 `frontend:3000`。

默认公开入口：

```text
http://<server-ip>:8088
```

域名和 HTTPS 推荐由宿主机 nginx、云负载均衡或其他边缘代理负责，再转发到 `127.0.0.1:8088` 或服务器安全组允许的 `PUBLIC_HTTP_PORT`。

宿主机 nginx 示例：

```nginx
server {
    listen 80;
    server_name skybooker.example.com;

    location / {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

HTTPS 可用 Certbot 或云厂商证书配置。证书私钥、签发证书和服务器本地 nginx 配置不要提交到仓库。

## 9. 验证和 Smoke

安装后检查：

```bash
cd /opt/skybooker
sudo ./deploy.sh status
curl http://localhost:8088/healthz
curl 'http://localhost:8088/api/flights?page=1&size=1'
```

如果服务器上有仓库 checkout，可运行 smoke 脚本：

```bash
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
```

Smoke 覆盖公共航班查询、用户/管理员登录边界、订单、AI 聊天和管理员统计。更多测试重点见 `docs/12_TESTING_GUIDE.md`。

如果演示数据日期过期，在确认目标数据库后重新生成并导入 seed。不要使用 Flyway migration 承载大规模演示数据，也不要在生产库导入测试 seed。

```bash
cd /path/to/skybooker-flight
python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date <YYYY-MM-DD>
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql
```

确认目标库是演示/测试库后导入：

```bash
cd /path/to/skybooker-flight
set -a; source /opt/skybooker/.env; set +a
docker compose --env-file /opt/skybooker/.env -f /opt/skybooker/docker-compose.yml exec -T mysql \
  mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  < backend/src/main/resources/db/seed/seed-dev.sql
```

## 10. 备份、升级和回滚安全

升级前备份数据库：

```bash
cd /opt/skybooker
set -a; source ./.env; set +a
docker compose --env-file .env -f docker-compose.yml exec -T mysql \
  mysqldump -uroot -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  | gzip > /opt/skybooker/skybooker-backup-$(date +%Y%m%d%H%M%S).sql.gz
```

升级：

```bash
sudo ./deploy.sh update --tag sha-<new-commit-sha>
sudo ./deploy.sh status
curl 'http://localhost:8088/api/flights?page=1&size=1'
```

回滚：

```bash
sudo ./deploy.sh rollback --tag sha-<previous-commit-sha>
sudo ./deploy.sh status
```

如果失败来自不可逆数据库迁移，应用镜像回滚可能不足，需要按备份恢复流程处理数据库。不要修改已经在共享或生产环境执行过的 Flyway migration；新增 migration 替代。

## 11. 安全检查清单

部署到公网前确认：

- `.env` 没有进入 Git，且已备份到安全位置。
- `MYSQL_PASSWORD`、`JWT_SECRET`、`AI_CONFIG_ENC_KEY` 是强随机值。
- `OPENAPI_ENABLED=false`，除非明确需要临时排查。
- 默认管理员密码已修改。
- GHCR 私有镜像已在服务器完成 `docker login`。
- 只开放 80/443 或 `PUBLIC_HTTP_PORT`，不要公开 MySQL、Redis、backend、frontend 容器端口。
- `MAIL_PROVIDER=log` 只用于本地、演示或测试；真实邮件发送需要配置已验证发件域名和 `RESEND_API_KEY`。
- `AI_LLM_ENABLED=false` 是安全默认；启用时只在服务器 `.env` 或后台管理中保存真实 provider key。
- 生成的日志、smoke 报告、数据库备份、证书和本地环境文件不提交到仓库。

## 12. 常见问题

### Compose 提示缺少变量

运行：

```bash
sudo ./deploy.sh install --prepare-only
```

检查 `/opt/skybooker/.env` 是否包含 `BACKEND_IMAGE`、`FRONTEND_IMAGE`、`IMAGE_TAG`、`MYSQL_PASSWORD`、`JWT_SECRET`、`AI_CONFIG_ENC_KEY`。

### Backend unhealthy 或反复重启

先看后端日志：

```bash
cd /opt/skybooker
sudo ./deploy.sh logs --tail=200 backend
```

如果日志中出现：

```text
FlywayValidateException
Detected failed migration
```

说明数据库初始化迁移曾经中途失败，MySQL volume 中留下了失败记录。首次部署且没有重要数据时，可以重置数据库 volume 后重试：

```bash
cd /opt/skybooker
sudo docker compose --env-file .env -f docker-compose.yml down -v
sudo ./deploy.sh update
```

`down -v` 会删除数据库 volume，已有业务数据会丢失。生产环境不要直接执行，必须先备份并确认失败原因。

如果没有 Flyway 错误，再检查健康检查详情：

```bash
sudo docker inspect skybooker-backend-1 \
  --format '{{json .State.Health}}'
```

### 镜像拉取失败

确认镜像是否发布、tag 是否存在，以及服务器是否已登录 GHCR：

```bash
docker pull ghcr.io/yunluoxincheng/skybooker-flight/skybooker-backend:latest
docker pull ghcr.io/yunluoxincheng/skybooker-flight/skybooker-frontend:latest
```

### 前端请求 API 失败

生产前端镜像按 `NEXT_PUBLIC_API_BASE_URL=/api` 构建，请通过 nginx 入口访问页面，避免直接访问 frontend 容器。若使用独立域名或跨域 API，需要在 `.env` 中设置精确的 `CORS_ALLOWED_ORIGINS`，不能使用 `*`。

### OpenAPI 暴露

Swagger / Knife4j 默认关闭。只有排查时临时设置：

```env
OPENAPI_ENABLED=true
```

排查结束后改回 `false` 并重启 backend。
