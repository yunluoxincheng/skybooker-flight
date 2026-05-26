# 04_ARCHITECTURE：项目整体架构

## 1. 总体架构

本项目采用前后端分离架构。

```mermaid
flowchart LR
    User[普通用户] --> Frontend[Next.js 前端]
    Admin[管理员] --> Frontend
    Frontend --> Backend[Spring Boot REST API]
    Backend --> MySQL[(MySQL)]
    Backend --> Redis[(Redis)]
    Backend --> AI[AI 意图解析/规则引擎]
    Backend --> Flyway[Flyway Migration]
```

## 2. 前端架构

前端负责：

- 页面路由；
- 用户交互；
- 航班卡片展示；
- 筛选条件管理；
- AI 聊天界面；
- 订单流程步骤展示；
- 后台管理页面。

前端不直接访问数据库，所有数据通过后端 API 获取。

## 3. 后端架构

后端负责：

- 用户认证与权限控制；
- 航班查询；
- 座位状态管理；
- 订单状态流转；
- 库存并发控制；
- 候补排队；
- 退票与改签；
- AI 助手条件解析和航班推荐；
- 管理后台接口；
- 数据统计。

## 4. 模块划分

```text
backend
├── auth        认证与权限
├── user        用户
├── passenger   乘机人
├── airline     航司
├── airport     机场
├── flight      航班
├── seat        座位
├── order       订单
├── refund      退票
├── change      改签
├── waitlist    候补
├── ai          智能购票助手
├── admin       管理后台
└── common      公共模块
```

## 5. 典型业务流程：订票

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as Next.js 前端
    participant B as Spring Boot 后端
    participant DB as MySQL

    U->>F: 选择航班和座位
    F->>B: POST /api/orders
    B->>DB: 创建订单
    B->>DB: 条件更新座位 AVAILABLE -> LOCKED
    DB-->>B: 返回影响行数
    B-->>F: 返回订单信息
    U->>F: 模拟支付
    F->>B: POST /api/orders/{id}/pay
    B->>DB: 更新订单状态
    B->>DB: 更新座位 LOCKED -> SOLD
    B-->>F: 出票成功
```

## 6. 典型业务流程：退票与候补

```mermaid
sequenceDiagram
    participant U as 用户
    participant B as 后端
    participant DB as MySQL

    U->>B: 申请退票
    B->>DB: 校验订单状态
    B->>DB: 计算手续费
    B->>DB: 订单更新为 REFUNDED
    B->>DB: 座位更新为 AVAILABLE
    B->>DB: 查询最早已支付候补订单
    alt 存在可兑现候补订单
        B->>DB: 分配座位并更新为 SOLD
        B->>DB: 创建正式订单和乘机人快照
        B->>DB: 候补状态改为 SUCCESS
    end
    B-->>U: 退票成功
```

## 7. 典型业务流程：AI 智能购票助手

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as Next.js 前端
    participant B as Spring Boot 后端
    participant A as 规则/LLM 意图解析
    participant DB as MySQL

    U->>F: 我明天从上海去北京，便宜一点
    F->>B: POST /api/ai/chat
    B->>A: 解析购票需求
    A-->>B: 查询条件 JSON
    B->>DB: 查询可购买航班
    DB-->>B: 航班列表
    B-->>F: 回复文本 + 航班卡片 + 跳转链接
    F-->>U: 展示推荐结果
```

## 8. 架构原则

### AI 不直接生成业务数据

AI 只负责理解用户意图，不直接编造航班、价格和库存。

### 订单与座位强一致

订单状态变化必须同步影响座位状态。

### 管理端和用户端隔离

用户端强调体验，后台强调效率。

### 数据库迁移版本化

所有数据库结构和初始化数据由 Flyway 管理。
