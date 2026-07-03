# 12_TESTING_GUIDE：测试指南

## 1. 测试目标

测试目标是保证系统核心业务流程可用，尤其是订票库存控制、订单状态流转、退票候补和 AI 推荐。

## 2. 功能测试

### 用户认证

- 注册成功；
- 重复邮箱注册失败；
- 普通用户可通过用户端邮箱密码登录；
- 管理员不能通过用户端登录；
- 管理员可通过 `/admin` 后台入口输入管理员用户名和密码登录；
- 普通用户不能通过后台入口登录；
- 禁用 `admin_user.status` 后管理员不能登录后台；
- 部署环境中 `/admin` 能打开前端后台登录页，`/api/admin/auth/login` 能调用后端登录接口；
- 密码错误登录失败；
- 未登录访问订单接口失败；
- 普通用户访问 `/api/admin/**` 失败；
- `role = ADMIN` 的管理员可以访问后台接口。
- 管理员 Token 访问 `/api/auth/me`、`/api/auth/logout`、`/api/orders`、`/api/passengers`、`/api/waitlist`、`/api/ai` 失败。
- 普通用户 Token 访问 `/api/admin/me`、`/api/admin/logout` 失败。
- 后台普通用户管理接口不能禁用 `role = ADMIN` 的管理员账号，也不能禁用当前登录管理员自身。
- Flyway 默认账号密码哈希必须通过后端集成测试校验：`admin / SkyBooker@Init2026!` 只能登录管理端，`user1@example.com / User@123456` 只能登录用户端。

### 航班查询

- 按日期 + 航班号查询；
- 按日期 + 出发地 + 目的地查询；
- 按价格区间筛选；
- 按航空公司筛选；
- 按起飞时间筛选；
- 排序正确。

### 订票流程

- 选择可用座位；
- 创建订单；
- 座位变为 LOCKED；
- 模拟支付；
- 订单变为 ISSUED；
- 座位变为 SOLD。

### 退票流程

- 已出票订单可退票；
- 手续费计算正确；
- 订单变为 REFUNDED；
- 座位释放；
- 候补订单被触发。

### 候补流程

- 目标舱位无票时可提交候补并生成 `PENDING_PAYMENT` 候补单；
- 候补支付成功后状态为 `WAITING`；
- 有退票时优先兑现最早已支付候补用户；
- 候补成功后状态变为 `SUCCESS`，并生成正式订单；
- 候补取消或失败时发起退款，退款成功后状态变为 `REFUNDED`。

### AI 推荐

测试输入：

```text
你好
```

期望：返回 `TRAVEL_CHAT`，`intent = TRAVEL_CHAT`，不要求出发地、目的地或日期。

测试输入：

```text
北京有什么好玩
```

期望：返回 `TRAVEL_CHAT`，不包含具体航班号、价格、余票、库存或预订链接。

测试输入：

```text
我想去北京
```

期望：返回 `TRAVEL_CHAT`，澄清用户是要查询机票还是了解目的地/路线，不直接查库，不展示缺失字段 UI。

测试输入：

```text
查上海到北京机票
```

期望：返回 `FOLLOW_UP`，追问出发日期。

测试输入：

```text
我明天从上海去北京，便宜一点
```

期望：返回可购买航班推荐卡片，并包含跳转链接。

会话测试：

- 首次匿名调用返回不可猜测的 `sessionId`，后续携带该 `sessionId` 可以继续同一会话；
- 匿名会话不能通过数据库自增 ID 访问；
- 登录普通用户只能读取、追加和删除自己的 AI 会话；
- 管理员 Token 访问 AI 会话接口失败。

LLM 意图解析测试：

- `AI_LLM_ENABLED=false` 或未配置 LLM 凭据时，AI 助手继续使用规则解析；
- LLM 单元测试使用 mock provider JSON，不依赖真实 `AI_LLM_API_KEY`；
- LLM 集成测试通过 mock `LlmChatClient` 验证解析成功、失败降级、多轮上下文补全、非搜索文本安全回退和伪造航班字段忽略；
- 推荐航班、价格、余票、详情链接和预订链接必须来自数据库查询结果，不能来自 LLM 响应；
- 多轮补全时，最新 assistant `FOLLOW_UP` 且有 `missingFields` 时，短回复如“明天”必须优先按 `FLIGHT_QUERY_CONTINUATION` 合并上下文（判定依据是“当前回复是否在补全航班查询条件”，不使用文本长度启发式）；
- `最近几天`、`未来一周`、`7月6日到7月8日`、`周一周二都可以` 等多日表达必须追问一个具体出发日期，不能任意查询单日。

## 3. 并发测试

目标：验证多人同时抢同一座位不会超卖。

### JMeter 测试思路

1. 准备一个可售座位；
2. 创建多个线程同时请求创建订单接口；
3. 请求参数使用同一个 `seatId`；
4. 期望只有一个请求成功；
5. 数据库中该座位最终只能被一个订单占用。

### 判断标准

- 成功订单数 = 1；
- 失败订单返回“座位已被占用”；
- flight_seat 中该座位状态为 LOCKED 或 SOLD；
- 不存在多个订单绑定同一个座位。

### 可复现 JMeter 报告工作流

仓库提供同座位并发下单测试计划、中文报告模板和报告运行脚本：

```text
scripts/jmeter/same-seat-order-race.jmx
scripts/jmeter/run-same-seat-concurrency-report.sh
scripts/jmeter/same-seat-concurrency-report-template.md
```

运行前准备：

1. 启动 MySQL、Redis 和后端；
2. 确认本机可执行 `jmeter`，或通过 `JMETER_BIN` 指向 JMeter 可执行文件；
3. 如演示数据日期过期，执行 `scripts/refresh-demo-flight-dates.sql`；
4. 选择一个属于普通测试用户的 `passenger.id`；
5. 选择一个未来已发布航班的 `AVAILABLE` 座位，记录 `flight_id` 和 `seat_id`，并确认该座位没有既有 `order_passenger` 绑定；
6. 准备数据库校验所需的 `MYSQL_PASSWORD`，必要时配置 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USER` 或 `MYSQL_CONTAINER`。

查询乘机人：

```sql
SELECT p.id AS passenger_id
FROM passenger p
JOIN users u ON u.id = p.user_id
WHERE u.email = '<user_email>'
ORDER BY p.id
LIMIT 1;
```

查询可用座位：

```sql
SELECT s.flight_id, s.id AS seat_id, s.seat_no
FROM flight_seat s
JOIN flight f ON f.id = s.flight_id
WHERE s.status = 'AVAILABLE'
  AND f.publish_status = 'PUBLISHED'
  AND f.departure_time > NOW()
  AND NOT EXISTS (
    SELECT 1
    FROM order_passenger op
    WHERE op.seat_id = s.id
  )
ORDER BY s.id
LIMIT 1;
```

生成报告：

```bash
BASE_URL=http://localhost:8080 \
USER_EMAIL=<user_email> \
USER_PASSWORD='<user_password>' \
FLIGHT_ID=<flight_id> \
PASSENGER_ID=<passenger_id> \
SEAT_ID=<seat_id> \
THREADS=20 \
MYSQL_PASSWORD='<mysql_password>' \
scripts/jmeter/run-same-seat-concurrency-report.sh
```

每次运行会创建一个时间戳证据目录：

```text
reports/jmeter/<timestamp>/
├── command-summary.md
├── database-verification.txt
├── html/
├── jmeter-output.log
├── runner.log
├── same-seat-order-race.jtl
├── summary-template.md
└── summary.md
```

报告脚本会自动执行 `scripts/concurrency/verify-same-seat-order-race.sh`，并把数据库校验输出保存到同一证据目录。如果宿主机没有 `mysql` 客户端，校验脚本会在 `MYSQL_CONTAINER` 指定的容器运行时自动改用 `docker exec` 查询数据库，默认容器名为 `skybooker-mysql`。

期望：

- JMeter 中只有一个“同座位创建订单”请求成功；
- 其他请求返回座位占用或业务规则失败；
- 校验脚本输出目标座位的订单乘机人绑定行数为 1，目标座位绑定的不同订单数为 1；
- 中文 `summary.md` 的最终结论为通过；
- 生成的 `.jtl`、HTML 报告、日志和本地摘要保存在 `reports/`，默认不提交到 Git。

报告脚本会在缺少必要变量、找不到 JMeter、JMeter 中同座位创建订单成功数不是 1、数据库校验失败时退出失败。如果本地 JMeter、数据库或演示数据不可用，只记录明确的跳过原因，不要把该输出标记为成功并发报告。

JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；仓库提交的说明文档、脚本输出和正式中文摘要以中文为准。

## 3.1 部署烟测

后端部署后运行：

```bash
SKYBOOKER_BASE_URL=http://localhost:8080 scripts/smoke/backend-smoke.sh
```

通过 Nginx API 网关运行：

```bash
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
```

服务器公网验证：

```bash
# 纯 IP
SKYBOOKER_BASE_URL=http://<server-ip>:8088 scripts/smoke/backend-smoke.sh

# 域名
SKYBOOKER_BASE_URL=https://skybooker.example.com scripts/smoke/backend-smoke.sh
```

脚本检查：

- 公共航班查询；
- 普通用户登录和 `/api/auth/me`；
- 管理员登录和 `/api/admin/me`；
- 普通用户不能访问管理员身份接口；
- 管理员不能访问用户身份接口；
- 普通用户订单列表；
- 匿名 AI 聊天响应；
- 管理员数据统计摘要。

高级报表验证（`/api/admin/reports/**`）仅在报表端点已启用时覆盖，不作为 Smoke 脚本的必要检查项。如需手动验证：

```bash
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$SKYBOOKER_BASE_URL/api/admin/reports/sales-trend?startDate=2025-01-01&endDate=2026-12-31&granularity=MONTH"
```

可配置变量：

```text
SKYBOOKER_BASE_URL
SKYBOOKER_SMOKE_OUTPUT_DIR
SKYBOOKER_USER_EMAIL
SKYBOOKER_USER_PASSWORD
SKYBOOKER_ADMIN_USERNAME
SKYBOOKER_ADMIN_PASSWORD
```

输出默认写入：

```text
reports/smoke/
```

该目录默认不提交到 Git。PR 或演示中只需要贴关键命令、HTTP 结果摘要和必要截图。

## 4. 接口测试

推荐使用：

- Apifox；
- Postman；
- Knife4j；
- curl。

## 5. 前端测试

重点检查：

- 页面是否正常加载；
- 筛选条件是否反映到 URL；
- AI 推荐按钮是否能正确跳转；
- 选座状态是否显示正确；
- 管理后台表格是否可分页和搜索。

## 6. 演示测试清单

演示前至少确认：

- 后端能启动；
- Flyway 脚本能执行；
- 前端能访问；
- 登录账号可用；
- 有足够演示航班数据；
- 有一个余票充足的航班；
- 有一个目标舱位无票的航班用于候补演示；
- AI 助手能返回推荐结果；
- 管理后台能看到数据统计。
