# 08_AI_CUSTOMER_SERVICE：AI 智能购票助手设计

## 1. 模块定位

AI 智能购票助手不是普通聊天机器人，而是航班查询和购票流程的智能入口。

它的目标是：

```text
理解用户自然语言需求
↓
提取购票条件
↓
查询真实可购买航班
↓
推荐前三个航班
↓
提供可点击跳转链接
```

## 2. 基本原则

### AI 不编造航班

航班号、价格、库存、状态必须来自数据库。

### AI 只负责理解意图

AI 或规则解析模块只负责将用户自然语言转换成结构化查询条件。

### 推荐结果可点击

每个推荐航班必须包含：

- 查看详情链接；
- 立即预订链接；
- 查看全部筛选结果链接。

## 3. 用户交互流程

### 场景一：信息不足

用户输入：

```text
我想去北京
```

系统判断缺少：

- 出发地；
- 出发日期。

AI 回复：

```text
可以的。请问你想从哪个城市出发？计划哪一天出行？
```

快捷按钮：

```text
今天
明天
后天
周末
价格低优先
时间短优先
```

### 场景二：信息完整

用户输入：

```text
我明天从上海去北京，便宜一点
```

解析结果：

```json
{
  "intent": "SEARCH_FLIGHT",
  "departureCity": "上海",
  "arrivalCity": "北京",
  "departureDate": "2026-05-26",
  "sort": "PRICE_ASC",
  "limit": 3,
  "missingFields": []
}
```

系统查询数据库后返回推荐卡片。

## 4. 可提取字段

| 字段 | 说明 |
|---|---|
| departureCity | 出发城市 |
| arrivalCity | 到达城市 |
| departureDate | 出发日期 |
| passengerCount | 乘机人数 |
| cabinClass | 舱位 |
| pricePreference | 价格偏好 |
| timePreference | 时间偏好 |
| airlinePreference | 航司偏好 |
| directOnly | 是否直飞 |
| baggageRequired | 是否需要行李额 |
| sort | 排序方式 |

## 5. 用户表达映射

| 用户表达 | 系统字段 |
|---|---|
| 便宜一点 / 省钱 / 低价 | sort = PRICE_ASC |
| 快一点 / 时间短 | sort = DURATION_ASC |
| 上午出发 | departureTimeRange = 06:00-12:00 |
| 下午出发 | departureTimeRange = 12:00-18:00 |
| 晚上走 | departureTimeRange = 18:00-24:00 |
| 不想中转 | directOnly = true |
| 有托运行李 | baggageRequired = true |
| 南航 / 南方航空 | airlineName = 南方航空 |
| 明天 | 当前日期 + 1 天 |
| 后天 | 当前日期 + 2 天 |
| 周末 | 最近的周六或周日 |

## 6. 推荐排序

支持排序：

```text
COMPREHENSIVE 综合推荐
PRICE_ASC     价格低优先
DURATION_ASC  飞行时间短优先
TIME_ASC      起飞时间早优先
SEATS_DESC    余票多优先
PUNCTUAL_DESC 准点率高优先
```

## 7. 默认实现：规则解析版

规则解析流程：

```text
用户输入
↓
关键词匹配
↓
城市名匹配
↓
日期表达转换
↓
缺失字段检查
↓
查询航班
```

优点：

- 稳定；
- 无外部依赖；
- 适合演示；
- 成本为零。

## 8. 增强实现：LLM 解析版

LLM 提示词核心要求：

```text
你是机票查询条件解析器。你只能把用户的购票需求解析成 JSON。
不要编造航班、价格、库存、机场或航空公司。
如果缺少必要字段，请在 missingFields 中列出，并给出 followUpQuestion。
```

LLM 输出示例：

```json
{
  "intent": "SEARCH_FLIGHT",
  "departureCity": "上海",
  "arrivalCity": "北京",
  "departureDate": "2026-05-26",
  "passengerCount": 1,
  "timePreference": null,
  "pricePreference": "LOW_PRICE",
  "sort": "PRICE_ASC",
  "missingFields": [],
  "followUpQuestion": null
}
```

## 9. 后端处理流程

```text
AiChatController
↓
AiChatService
↓
IntentParserService
↓
FlightRecommendationService
↓
FlightMapper 查询数据库
↓
返回 AiChatResponse
```

## 10. 前端展示

AI 返回结果时，前端展示：

- AI 回复文本；
- 航班推荐卡片；
- 查看详情按钮；
- 立即预订按钮；
- 查看全部结果按钮。

示例卡片：

```text
东方航空 MU5101
上海虹桥 → 北京首都
08:30 - 10:45
飞行时长：2小时15分钟
￥680 起
剩余 24 座
准点
[查看详情] [立即预订]
```

## 11. 数据保存

每次聊天保存：

- 用户消息；
- AI 回复；
- 解析出的查询条件；
- 推荐航班 ID；
- 跳转链接。

会话保存规则：

- 对外返回的 `sessionId` 对应 `ai_chat_session.public_session_id`，不暴露数据库自增主键；
- 匿名用户会话 `user_id = NULL`，依赖不可猜测的 `sessionId` 继续会话；
- 登录普通用户会话写入 `user_id`，后续读取、追加消息或删除时必须校验归属；
- 管理员 Token 不允许访问 AI 助手接口。

这样可以用于后续运营分析和展示。
