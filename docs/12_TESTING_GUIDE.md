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
- Flyway 默认账号密码哈希必须通过后端集成测试校验：`admin / Admin@123456` 只能登录管理端，`user1@example.com / User@123456` 只能登录用户端。

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
我想去北京
```

期望：追问出发地和日期。

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

### 可复现 JMeter 资产

仓库提供同座位并发下单测试计划：

```text
scripts/jmeter/same-seat-order-race.jmx
```

运行前准备：

1. 启动 MySQL、Redis 和后端；
2. 如演示数据日期过期，执行 `scripts/refresh-demo-flight-dates.sql`；
3. 选择一个属于 `user1@example.com` 的 `passenger.id`；
4. 选择一个未来已发布航班的 `AVAILABLE` 座位，记录 `flight_id` 和 `seat_id`。

运行示例：

```bash
mkdir -p reports/jmeter
jmeter -n \
  -t scripts/jmeter/same-seat-order-race.jmx \
  -l reports/jmeter/same-seat-order-race.jtl \
  -e -o reports/jmeter/same-seat-order-race-html \
  -JBASE_URL=http://localhost:8080 \
  -JUSER_EMAIL=user1@example.com \
  -JUSER_PASSWORD='User@123456' \
  -JFLIGHT_ID=<flight_id> \
  -JPASSENGER_ID=<passenger_id> \
  -JSEAT_ID=<seat_id> \
  -JTHREADS=20
```

数据库校验：

```bash
SEAT_ID=<seat_id> MYSQL_PASSWORD=<password> scripts/concurrency/verify-same-seat-order-race.sh
```

如果宿主机没有 `mysql` 客户端，校验脚本会在 `skybooker-mysql` 容器运行时自动改用 `docker exec` 查询数据库。

期望：

- JMeter 中只有一个 `Create order for same seat` 请求业务成功；
- 其他请求返回座位占用或业务规则失败；
- 校验脚本输出 `Order-passenger rows for target seat: 1` 和 `Distinct order bindings for target seat: 1`；
- 生成的 `.jtl`、HTML 报告和日志保存在 `reports/`，默认不提交到 Git。

## 3.1 部署烟测

后端部署后运行：

```bash
SKYBOOKER_BASE_URL=http://localhost:8080 scripts/smoke/backend-smoke.sh
```

通过 Nginx API 网关运行：

```bash
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
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
