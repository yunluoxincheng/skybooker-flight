# scripts

该目录用于放置项目辅助脚本，例如：

- 本地启动脚本；
- 数据库重置脚本；
- Docker 构建脚本；
- 演示数据刷新脚本。

当前提供：

- `refresh-demo-flight-dates.sql`：演示前将航班日期刷新到明天，并按订单 ID 生成唯一演示订单号，同步支付时间。
- `smoke/backend-smoke.sh`：部署后烟测脚本，检查公共航班查询、用户/管理员登录边界、用户订单、AI 聊天和管理员统计。
- `jmeter/same-seat-order-race.jmx`：JMeter 同座位并发下单测试计划。
- `jmeter/run-same-seat-concurrency-report.sh`：JMeter 同座位并发报告运行脚本，生成时间戳证据目录、HTML 报告、运行日志、数据库校验输出和中文摘要。
- `jmeter/same-seat-concurrency-report-template.md`：同座位并发测试中文报告摘要模板。
- `jmeter/README.md`：JMeter 测试数据准备、报告运行命令、证据目录和期望结果。
- `concurrency/verify-same-seat-order-race.sh`：并发测试后的数据库校验脚本，验证目标座位只有一行绑定且只绑定一个订单。

生成的烟测日志、JMeter `.jtl`、HTML 报告和截图统一放在 `reports/` 下，默认不提交到 Git。
JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；仓库提交的说明文档和正式摘要报告使用中文。
