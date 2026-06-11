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
- `jmeter/README.md`：JMeter 测试数据准备、运行命令和期望结果。
- `concurrency/verify-same-seat-order-race.sh`：并发测试后的数据库校验脚本，验证目标座位只有一行绑定且只绑定一个订单。

生成的烟测日志、JMeter `.jtl`、HTML 报告和截图统一放在 `reports/` 下，默认不提交到 Git。
