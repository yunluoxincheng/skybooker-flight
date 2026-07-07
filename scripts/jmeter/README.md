# JMeter 同座位并发下单报告

本目录保存 SkyBooker 同座位并发下单测试计划和报告生成脚本。生成的 `.jtl`、HTML 报告、运行日志、数据库校验输出和本地摘要统一写入 `reports/jmeter/<timestamp>/`，该目录默认被 Git 忽略。

## 前置条件

- 已启动 MySQL、Redis 和后端服务；
- 本机可执行 `jmeter`，或通过 `JMETER_BIN` 指向 JMeter 可执行文件；
- 测试数据库中存在未来已发布航班、可用座位和属于普通用户的乘机人；
- 如演示数据日期过期，重新生成并导入 dev seed：

```bash
python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date <YYYY-MM-DD>
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql
mysql --default-character-set=utf8mb4 -h localhost -P 3306 -u root -p flight_booking \
  < backend/src/main/resources/db/seed/seed-dev.sql
```

## 查询测试数据

选择一个属于测试用户的 `passenger.id`：

```sql
SELECT p.id AS passenger_id
FROM passenger p
JOIN users u ON u.id = p.user_id
WHERE u.email = '<user_email>'
ORDER BY p.id
LIMIT 1;
```

选择一个未来已发布航班的可用座位，并记录 `flight_id` 和 `seat_id`。为避免历史取消订单影响数据库校验，目标座位应没有既有 `order_passenger` 绑定：

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

## 生成报告

推荐使用报告运行脚本，它会创建时间戳证据目录、运行 JMeter、生成 HTML 报告、执行数据库校验，并输出中文 `summary.md`：

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

可选变量：

```text
REPORT_ROOT      默认 reports/jmeter
RUN_TIMESTAMP    默认当前时间，格式 yyyyMMdd-HHmmss
EVIDENCE_DIR     指定完整证据目录时覆盖 REPORT_ROOT/RUN_TIMESTAMP
JMETER_BIN       默认 jmeter
MYSQL_HOST       默认 localhost
MYSQL_PORT       默认 3306
MYSQL_DB         默认 flight_booking
MYSQL_USER       默认 root
MYSQL_CONTAINER  默认 skybooker-mysql
```

## 证据目录

一次成功运行会生成：

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

`command-summary.md` 和 `runner.log` 会对 `USER_PASSWORD`、`MYSQL_PASSWORD`、Token、JWT、Secret、API Key 等敏感值做脱敏处理。生成目录仍可能包含本地路径、数据库状态和演示环境信息，因此默认不提交到 Git。

## 期望结果

- JMeter 中“同座位创建订单”采样器的成功数必须等于 1；
- 其余同座位创建订单请求应因为座位占用或业务规则失败；
- 数据库校验输出应显示目标座位只有 1 行订单乘机人绑定，并且只绑定 1 个不同订单；
- `summary.md` 的最终结论为通过。

如果 JMeter、数据库或测试数据不可用，不要把本地输出作为成功报告；应记录具体缺失前置条件，并只提交脚本、模板和文档。

## 语言边界

仓库维护的 README、测试计划名称、采样器标签、脚本输出和 Markdown 摘要均使用中文。JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；课程报告和 PPT 以中文 `summary.md` 与数据库校验输出为准。
