# scripts

该目录用于放置项目辅助脚本，例如：

- 本地启动脚本；
- 数据库重置脚本；
- Docker 构建脚本；
- 演示数据刷新脚本。

当前提供：

- `generate_test_data.py`：按 dev/test/perf profile 生成可复现 seed SQL，输出到 `backend/src/main/resources/db/seed/seed-*.sql`。
- `validate_test_data.py`：静态校验生成的 seed SQL，检查规模、核心表、状态覆盖和生成器一致性标志。
- `refresh-demo-flight-dates.sql`：历史兼容占位；Flyway 不再自动插入演示航班，演示日期请通过重新生成 seed 刷新。
- `seed-connecting-itineraries.sql`：仅供一次中转联程功能本地验收，在一次性演示库生成北京→上海→广州的有效两段组合；不会由 Flyway 自动执行。
- `smoke/backend-smoke.sh`：部署后烟测脚本，检查公共航班查询、用户/管理员登录边界、用户订单、AI 聊天和管理员统计。
- `jmeter/same-seat-order-race.jmx`：JMeter 同座位并发下单测试计划。
- `jmeter/run-same-seat-concurrency-report.sh`：JMeter 同座位并发报告运行脚本，生成时间戳证据目录、HTML 报告、运行日志、数据库校验输出和中文摘要。
- `jmeter/same-seat-concurrency-report-template.md`：同座位并发测试中文报告摘要模板。
- `jmeter/README.md`：JMeter 测试数据准备、报告运行命令、证据目录和期望结果。
- `concurrency/verify-same-seat-order-race.sh`：并发测试后的数据库校验脚本，验证目标座位只有一行绑定且只绑定一个订单。

生成的烟测日志、JMeter `.jtl`、HTML 报告和截图统一放在 `reports/` 下，默认不提交到 Git。
JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；仓库提交的说明文档和正式摘要报告使用中文。

## 测试数据 seed

生成和校验：

```bash
python3 scripts/generate_test_data.py --profile dev --seed 20260707
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql

python3 scripts/generate_test_data.py --profile test --seed 20260707
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-test.sql

# 性能测试数据规模较大，仅在压测环境生成
python3 scripts/generate_test_data.py --profile perf --seed 20260707
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-perf.sql
```

Docker Compose MySQL 导入：

```bash
docker exec -i skybooker-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-flight_booking}"' \
  < backend/src/main/resources/db/seed/seed-dev.sql
```

导入 perf 数据时把文件换成 `seed-perf.sql`。`perf` 只用于性能测试或压测环境，不建议导入日常开发库，也不要提交生成后的 `seed-perf.sql` 到 Git。

完整说明见 `docs/17_TEST_DATA_GUIDE.md`。

`refresh-demo-flight-dates.sql` 现在只保留为兼容占位。Flyway 不再自动插入演示航班，刷新演示日期请重新生成 seed SQL。
