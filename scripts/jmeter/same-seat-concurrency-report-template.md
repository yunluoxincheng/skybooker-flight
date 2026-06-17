# SkyBooker 同座位并发下单 JMeter 报告摘要

## 1. 测试目的

验证多个并发请求同时购买同一 `seatId` 时，系统不会出现超卖，最终只能有一个订单绑定目标座位。

## 2. 测试环境

- 后端地址：`<BASE_URL>`
- 测试数据库：`<MYSQL_HOST>:<MYSQL_PORT>/<MYSQL_DB>`
- JMeter 版本：`<JMETER_VERSION>`
- 测试时间：`<YYYY-MM-DD HH:mm:ss>`
- 证据目录：`reports/jmeter/<timestamp>/`

## 3. 测试目标

- 航班 ID：`<flight_id>`
- 乘机人 ID：`<passenger_id>`
- 座位 ID：`<seat_id>`
- 并发线程数：`<threads>`

## 4. JMeter 结果

- 同座位创建订单请求数：`<total_order_create_requests>`
- 成功数：`<success_count>`
- 失败数：`<failure_count>`
- `.jtl` 文件：`reports/jmeter/<timestamp>/same-seat-order-race.jtl`
- HTML 报告：`reports/jmeter/<timestamp>/html/`

## 5. 数据库校验

校验输出文件：`reports/jmeter/<timestamp>/database-verification.txt`

```text
<粘贴或保留脚本生成的数据库校验输出>
```

## 6. 最终结论

`<通过/未通过>`：同一座位并发下单场景中，只有 1 个创建订单请求成功，数据库最终只有 1 个订单乘机人座位绑定，不存在重复占座。

> 说明：JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；课程报告和 PPT 以本中文摘要及数据库校验输出为准。
