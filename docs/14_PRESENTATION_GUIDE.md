# 14_PRESENTATION_GUIDE：展示指南

## 0. 展示名称

PPT 首页建议标题：

```text
SkyBooker 云航智订
基于 Next.js + Spring Boot 的智能航班查询与机票预订系统
```

## 1. PPT 建议结构

1. 项目背景；
2. 需求分析；
3. 技术选型；
4. 系统架构；
5. 数据库设计；
6. 核心功能展示；
7. AI 智能购票助手；
8. 并发库存控制；
9. 部署与测试；
10. 总结与分工。

## 2. 系统演示顺序

建议演示顺序：

```text
首页
↓
AI 智能购票助手
↓
点击推荐航班进入详情
↓
进入预订页
↓
选择乘机人和座位
↓
提交订单并模拟支付
↓
查看我的订单
↓
退票并触发候补
↓
进入管理员后台查看订单和数据统计
↓
调用高级报表接口展示销售趋势、航线表现、客座率、退票趋势和候补表现
```

## 2.1 演示前检查清单

演示前至少完成：

- 从 `main` 或本次演示分支启动最新代码；
- `.env` 已配置 `MYSQL_PASSWORD`、`JWT_SECRET`、数据库名、端口和 `OPENAPI_ENABLED`；
- `docker compose up -d --build` 后 `mysql`、`redis`、`backend`、`nginx` 均为 healthy；
- `curl http://localhost:8088/healthz` 返回 `ok`；
- `curl http://localhost:8088/api/flights?page=1\&size=1` 返回标准 API 包装；
- 如航班日期已过期，重新生成并导入 `seed-dev.sql`，详见 `docs/17_TEST_DATA_GUIDE.md`；
- `scripts/smoke/backend-smoke.sh` 通过，输出保存在 `reports/smoke/`；
- AI 助手对”我想去北京”能返回追问或推荐响应；
- 准备一个余票充足航班用于订票演示；
- 准备一个目标舱位无票航班用于候补演示；
- 准备一个已出票订单用于退票和改签演示；
- 管理后台数据统计接口可访问；
- 管理后台高级报表接口可访问，并准备固定日期范围用于截图或接口演示；
- JMeter 同座位并发报告工作流已跑过，`reports/jmeter/<timestamp>/summary.md`、HTML 报告截图和数据库校验结果可在 PPT 或答辩中展示。

### 服务器部署演示就绪

如需在服务器上部署并演示，额外确认：

- [ ] 服务器 `docker compose ps` 全部 healthy；
- [ ] `scripts/smoke/backend-smoke.sh` 通过 `SKYBOOKER_BASE_URL=http://<server-ip>:8088` 验证；
- [ ] 管理员密码已从默认 `SkyBooker@Init2026!` 修改；
- [ ] 演示数据日期未过期（必要时重新生成并导入 `seed-dev.sql`）；
- [ ] 前端可通过公网访问或由前端同学另行启动；
- [ ] 保存以下证据用于答辩或 PR：
  - `docker compose ps` 输出截图；
  - Smoke 脚本输出摘要；
  - `/healthz` 和 `/api/flights` curl 结果；
  - 如有前端页面，浏览器访问截图。

当前 `frontend/` 目录没有 Next.js 工程或构建输入，本分支只提供后端容器和 Nginx API 网关。展示前端页面时，需要由前端工程另行启动；本仓库的 Nginx `/api/` 路由可作为前端 API 接入地址。

## 3. 重点讲解内容

### AI 智能购票助手

强调：

- 用户自然语言输入；
- 系统解析购票条件；
- 查询数据库真实航班；
- 返回推荐卡片；
- 点击进入购买流程。

### 并发库存控制

强调：

- 座位有状态；
- 使用数据库条件更新；
- 防止多人同时购买同一座位；
- 使用事务保证一致性。
- 可展示 `scripts/jmeter/same-seat-order-race.jmx`、`scripts/jmeter/run-same-seat-concurrency-report.sh` 和 `scripts/concurrency/verify-same-seat-order-race.sh` 的运行结果，证明同一座位最终只绑定一个订单。
- 加分项 PPT 建议放 1 页“JMeter 同座位并发验证”：展示线程数、目标航班/座位、成功数 1、失败数 N-1、`database-verification.txt` 中目标座位绑定行数为 1，以及 `summary.md` 的最终结论。
- 推荐证据包括 `reports/jmeter/<timestamp>/summary.md` 摘要截图、JMeter HTML 报告关键图表截图、数据库校验输出截图和证据目录结构截图。JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；正式讲解和课程报告以中文摘要为准。

### Flyway

强调：

- 数据库结构版本化；
- 项目启动自动建表；
- 更符合真实项目开发流程。

### 管理后台高级报表

强调：

- 基础 `/api/admin/dashboard/**` 看板不变，高级报表新增在 `/api/admin/reports/**`；
- 销售趋势使用 `ticket_order.pay_time`，活跃订单包含 `ISSUED` 和 `CHANGED`；
- 航线表现同时展示收入、退款和净收入，能看到只有退款没有同期销售的航线；
- 航班客座率使用 `flight.departure_time` 和已出票/已改签乘机人数量计算；
- 退票趋势使用 `refund_record.created_at`，候补表现使用 `waitlist_order.created_at`；
- 候补表现展示 `submittedCount` 以及 `pendingPaymentCount`、`waitingCount`、`successCount`、`failedCount`、`cancelledCount`、`refundedCount`、`expiredCount`，状态计数之和等于提交数；
- 可在答辩中展示 `AdminReportIntegrationTest` 结果，证明权限、日期范围、粒度、限制条数、空周期补零和金额汇总均已覆盖。

## 4. 视频录制建议

视频控制在 5 到 8 分钟。

建议录制：

- 前端页面；
- 后端接口文档；
- 数据库表；
- Flyway 脚本；
- AI 助手推荐；
- 订票流程；
- 后台管理。

## 5. 老师可能关注的问题

### 问：AI 推荐的航班是真实的吗？

答：推荐结果不是 AI 编造的，AI 或规则模块只负责解析用户需求，航班号、价格、余票和状态都来自数据库。

### 问：如何防止超卖？

答：创建订单时使用数据库条件更新和乐观锁，只有座位状态为 AVAILABLE 且 version 匹配时才能锁定成功。

### 问：为什么用 Flyway？

答：Flyway 可以让数据库结构和初始化数据版本化，项目启动时自动迁移，避免手动导入 SQL。

### 问：为什么前端用 Next.js？

答：Next.js 路由清晰、组件生态成熟，适合构建用户端和管理端页面，也方便后续扩展。
