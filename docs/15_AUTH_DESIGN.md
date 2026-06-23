# 15_AUTH_DESIGN：认证与登录注册设计

## 1. 设计目标

SkyBooker 云航智订需要提供完整、稳定、适合实训落地的认证能力。认证模块不仅服务于登录注册，也负责保护订单、乘机人、后台管理等需要身份校验的接口。

最终主方案：

```text
用户端：邮箱验证码注册 + 邮箱密码登录 + 邮箱验证码找回密码 + JWT 登录态管理
管理端：管理员用户名密码登录 + JWT 登录态管理
```

用户端和管理端登录入口必须隔离：

- 普通用户只能通过用户端登录页和 `/api/auth/login` 登录；
- 管理员只能通过管理后台登录页 `/admin` 和 `/api/admin/auth/login` 登录；
- `role = USER` 不允许登录管理后台；
- `role = ADMIN` 不允许登录用户端。

## 2. 方案选择

### 为什么选择邮箱验证码

邮箱验证码适合实训项目，原因是：

- 成本低；
- 实现难度适中；
- 不需要企业短信签名和模板审核；
- 可以使用 SMTP 或 Mock 模式完成演示；
- 比单纯账号密码注册更接近真实系统。

### 为什么不使用手机号验证码作为主方案

短信验证码通常需要：

- 短信服务商账号；
- 实名认证；
- 短信签名；
- 模板审核；
- 付费额度。

因此手机号验证码只作为后续扩展能力。开发环境可以提供 Mock 手机验证码，但不作为主流程。

### 为什么第三方登录作为扩展

微信、支付宝登录通常涉及开放平台应用申请、回调域名、审核、密钥管理等流程，不适合作为实训主线。系统预留 `oauth_account` 表，后续可扩展微信、支付宝、GitHub 等第三方登录。

## 3. 用户注册流程

```text
用户输入邮箱
↓
点击发送验证码
↓
后端校验发送频率
↓
生成 6 位数字验证码
↓
验证码写入 Redis，有效期 5 分钟
↓
邮件服务发送验证码
↓
用户提交邮箱、验证码、昵称、密码、确认密码
↓
后端校验验证码、邮箱唯一性、密码强度
↓
BCrypt 加密密码
↓
创建用户
↓
注册成功
```

Redis Key：

```text
auth:email-code:register:{email}
```

## 4. 用户登录流程

```text
用户输入邮箱和密码
↓
后端查询用户
↓
校验账号状态
↓
BCrypt 校验密码
↓
生成 JWT accessToken
↓
返回 Token 和用户信息
```

前端请求受保护接口时携带：

```http
Authorization: Bearer <token>
```

## 5. 找回密码流程

```text
用户输入邮箱
↓
发送找回密码验证码
↓
用户提交验证码、新密码、确认密码
↓
后端校验验证码
↓
更新密码哈希
↓
清理 Redis 验证码
↓
要求用户重新登录
```

Redis Key：

```text
auth:email-code:reset-password:{email}
```

## 6. 管理员登录

管理员账号不开放注册，但管理员仍然是 `users` 表中的账号，使用 `role = ADMIN` 区分。

管理员来源：

- Flyway 初始化默认管理员；
- 后续由后台创建管理员账号。

管理员登录方式：

```text
管理员用户名 + 密码
```

管理后台访问入口：

```text
GET /admin
```

访问该地址时，未登录或 Token 无效则展示管理后台登录页；已登录且具备 `ADMIN` 角色则进入管理后台首页。

该地址是前端页面路由，不是后端 JSON API。部署时 `/admin` 由前端服务处理，`/api/admin/auth/login` 和其他 `/api/admin/**` 接口转发到后端服务。

管理员登录接口：

```text
POST /api/admin/auth/login
```

后端通过 `admin_user.username` 查询管理员扩展资料，再关联 `users` 校验密码哈希、`users.status`、`admin_user.status` 和 `role = ADMIN`。登录成功后签发只用于管理后台的 JWT。

`admin_user` 表保存管理员用户名和扩展资料，例如工号、真实姓名、备注和启用状态，但不保存密码。密码哈希仍保存在 `users.password_hash` 中。默认管理员密码必须在首次部署后修改。

## 7. 验证码安全限制

- 验证码默认 5 分钟有效；
- 同一邮箱 60 秒内不能重复发送；
- 同一邮箱每日最多发送 10 次；
- 同一 IP 每小时最多发送 20 次；
- 验证码错误 5 次后失效；
- 验证码只保存哈希或短期明文于 Redis，不长期落库；
- 发送记录写入 `auth_verification_code_log` 便于统计和排查。

## 8. 邮件服务设计

邮件服务抽象为 `MailService` 接口。

当前实现：

```text
LogMailService    本地开发、课堂演示和自动化测试，控制台打印验证码
ResendMailService 生产环境可选，通过 Resend HTTP API 发送真实邮件
```

默认 `MAIL_PROVIDER=log`，不需要邮件服务账号。生产环境启用真实邮件时设置 `MAIL_PROVIDER=resend`、`MAIL_FROM` 和 `RESEND_API_KEY`；Resend 模式会要求发件域名或发件地址已在 Resend 侧验证。`test` profile 始终使用日志邮件实现，避免自动化测试依赖外部网络或真实凭据。

验证码发送成功后才写入 Redis 并记录 `SUCCESS` 日志；如果邮件 provider 拒绝或超时，接口返回 `VERIFICATION_EMAIL_SEND_FAILED`，不留下可用验证码，不消耗邮箱每日成功发送额度，但仍计入 IP 小时限流。

## 9. 接口清单

```http
POST /api/auth/email-code
POST /api/auth/register
POST /api/auth/login
POST /api/auth/reset-password
GET  /api/auth/me
POST /api/auth/logout
GET  /admin
POST /api/admin/auth/login
GET  /api/admin/me
POST /api/admin/logout
```

访问控制矩阵：

| 入口或接口 | 允许身份 | Token 要求 | 说明 |
|---|---|---|---|
| `POST /api/auth/login` | `USER` | 无 | 用户端登录，`ADMIN` 账号必须拒绝 |
| `GET /api/auth/me` | `USER` | `loginPortal = USER` | 管理端 Token 必须拒绝 |
| `POST /api/auth/logout` | `USER` | `loginPortal = USER` | 管理员退出登录使用 `/api/admin/logout` |
| `GET /admin` | `ADMIN` | 前端校验管理端 Token | 前端页面路由，不是后端 JSON API |
| `POST /api/admin/auth/login` | `ADMIN` | 无 | 管理端登录，`USER` 账号必须拒绝 |
| `GET /api/admin/me` | `ADMIN` | `loginPortal = ADMIN` | 返回当前管理员资料 |
| `POST /api/admin/logout` | `ADMIN` | `loginPortal = ADMIN` | 用户端 Token 必须拒绝 |
| `/api/orders/**`、`/api/passengers/**`、`/api/waitlist/**` | `USER` | `loginPortal = USER` | 管理端 Token 不能访问用户端个人业务 |
| `/api/ai/**` | 匿名或 `USER` | 无 Token 或 `loginPortal = USER` | AI 助手属于用户端购票流程，管理端 Token 必须拒绝 |
| `/api/admin/**` | `ADMIN` | `loginPortal = ADMIN` | 除 `POST /api/admin/auth/login` 外均要求管理员身份 |

## 10. 数据库表

认证相关表：

```text
users       账号、密码哈希、角色和登录状态
admin_user  管理员用户名和扩展资料，不保存密码哈希
auth_verification_code_log
oauth_account
```

验证码本体存 Redis，数据库只记录发送日志。

## 11. 前端页面

```text
/login
/register
/forgot-password
```

用户端认证页面采用 SkyBooker 品牌化布局，主流程围绕邮箱验证码和邮箱密码登录，不要求用户填写手机号。

管理后台登录页位于：

```text
/admin
```

后台登录页只展示管理员用户名、密码和登录按钮，不提供普通用户注册、找回密码或用户端跳转入口。

Next.js 路由实现为 `src/app/admin/page.tsx`。

## 12. 扩展方向

后续可扩展：

- 邮箱验证码登录；
- 手机号验证码登录；
- 微信登录；
- 支付宝登录；
- GitHub 登录；
- Refresh Token；
- Token 黑名单；
- 登录设备管理。
