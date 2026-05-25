# 03_TECH_SELECTION：技术选型

## 1. 技术选型总览

本项目采用前后端分离架构：

```text
前端：Next.js + React + TypeScript + Tailwind CSS + shadcn/ui
后端：Spring Boot + Spring MVC + MyBatis + MySQL + Redis + Flyway
认证：Spring Security + JWT
接口文档：Knife4j / Swagger
部署：Docker + Docker Compose + Nginx
```

## 2. 前端选型

### Next.js

选择 Next.js 的原因：

- 路由结构清晰，适合组织用户端与后台管理端页面；
- 基于 React，生态成熟；
- App Router 适合构建现代页面；
- 支持组件化开发；
- 适合后续扩展 SEO、服务端渲染或静态生成；
- 与 Tailwind CSS、shadcn/ui 配合良好。

本项目中 Next.js 主要作为前端应用框架使用，页面通过 REST API 与 Spring Boot 后端通信。

### TypeScript

选择 TypeScript 的原因：

- 提供类型约束；
- 减少接口字段错误；
- 便于多人协作；
- AI 生成代码时更容易保持结构稳定。

### Tailwind CSS

选择 Tailwind CSS 的原因：

- 快速构建 UI；
- 样式与组件绑定清晰；
- 适合响应式布局；
- 与 shadcn/ui 天然适配。

### shadcn/ui

选择 shadcn/ui 的原因：

- 组件现代、简洁；
- 代码可控，不是黑盒组件库；
- 适合生成高质量 UI；
- 适合用户端卡片、后台表格、弹窗、表单等场景。

## 3. 后端选型

### Spring Boot

选择 Spring Boot 的原因：

- Java Web 主流框架；
- 适合实训项目和企业开发；
- 配置简洁；
- 生态完整；
- 适合构建 RESTful API。

### Spring MVC

用于实现 Controller 层接口，处理前端请求。

### MyBatis

本项目选择 MyBatis，而不是 MyBatis-Plus。

选择 MyBatis 的原因：

- 可以手写 SQL，适合体现 JavaWeb 实训中的数据库能力；
- 航班筛选查询条件多，手写动态 SQL 更灵活；
- 座位库存扣减和乐观锁更新需要精准控制 SQL；
- 便于在报告中说明复杂查询和事务处理。

典型场景：

- 多条件航班查询；
- 按价格、时间、余票排序；
- 座位状态条件更新；
- 候补订单优先级查询；
- 数据统计报表。

### MySQL

用于保存核心业务数据：用户、航班、座位、订单、退改签、候补和 AI 聊天记录。

### Redis

Redis 用于增强：

- 登录 Token 黑名单，可选；
- 座位锁短期缓存，可选；
- 订单支付超时标记，可选；
- 热门航线缓存，可选。

基础版本可以主要使用 MySQL 事务和乐观锁，Redis 作为加分增强。

### Flyway

Flyway 用于自动执行数据库迁移脚本。

优势：

- 不需要手动导入 SQL；
- 数据库结构版本可追踪；
- 团队开发环境保持一致；
- 适合真实项目工程化。

## 4. AI 智能助手选型

### 默认方案：规则解析

规则解析适合实训落地：

- 稳定；
- 不依赖外部 API；
- 成本低；
- 演示可控。

### 增强方案：LLM 意图解析

LLM 只负责将用户语言解析为 JSON，不直接生成航班结果。

```text
用户自然语言
↓
LLM 解析查询条件
↓
后端查询数据库
↓
业务规则排序
↓
返回真实航班
```

这样可以避免 AI 胡编航班。

## 5. 部署选型

### 本地开发

- MySQL + Redis 使用 Docker；
- 后端本地运行；
- 前端本地运行。

### 演示部署

- Docker Compose 一键启动；
- 前端、后端、MySQL、Redis 编排在一起。

### 服务器部署

- Nginx 反向代理；
- 前端 Next.js 服务；
- 后端 Spring Boot 服务；
- MySQL 和 Redis 独立容器或服务器服务。
