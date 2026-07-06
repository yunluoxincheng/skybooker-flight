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

增强后的助手会先做后端受控意图路由，再决定是否进入航班查询条件解析：

意图分两层判定：第一层做领域路由（是否属于旅游/出行领域、是否涉及航班事实），第二层进入对应执行器。

- `TRAVEL_CHAT`：旅游出行闲聊（含问候、目的地玩法、行程与出行准备建议），走 LLM 限话题回复，返回 `TRAVEL_CHAT`，不输出具体航班事实；
- `FLIGHT_QUERY` / `FLIGHT_QUERY_CONTINUATION`：涉及机票、航班、票价、余票、舱位、时刻等系统事实，进入条件解析、上下文合并和数据库推荐链路；`价格` / `多少钱` / `预算` 只有绑定明确航班词、查票/买票/订票意图，或路线+日期/舱位等查询信号时才进入该链路；
- `BOOKING_HELP`：订票、退票、改签、候补、乘机人、订单、账号等 SkyBooker 固定流程说明，返回 `BOOKING_HELP`，使用后端固定模板；
- `OUT_OF_SCOPE`：拒绝非旅行、非航班、非 SkyBooker 平台帮助请求。

旅游预算类问题（如“去北京玩三天要多少钱”“北京旅行预算多少钱”）属于 `TRAVEL_CHAT`，不应只因为出现“多少钱/预算”就进入航班查询。

多轮 continuation 只在上一条助手消息是 `FOLLOW_UP` 且带 `missingFields` 时触发，依据“当前回复是否在补全航班查询条件”判定（含航班信号、parser 解析出任一查询字段、或文本是已知城市名/乘客数等裸补全词），不使用“短回复长度”启发式。进入 continuation 后，即使当前回复已补齐必填字段，也会继承上一轮未被当前轮覆盖的可选筛选条件（如乘客数、舱位、航司、价格、时段、直飞、排序）。新的完整独立查询不会继承上一轮已完成推荐/无结果回复中的可选筛选条件，避免上下文污染。

旅游闲聊中识别出的目的地城市会作为受控上下文保存到助手回复的 `extraJson.travelContext.destinationCity`。后续用户明确查询机票但只补充出发地和日期时，后端可以从该上下文补齐目的地，例如先说“我想去上海迪士尼玩”，再说“我现在在广州，8月2到5号这几天的机票”，系统会把目的地补为上海。

## 2. 基本原则

### AI 不编造航班

航班号、价格、库存、状态必须来自数据库。

### AI 只负责理解意图和受限表达

AI 或规则解析模块只负责将用户自然语言转换成结构化查询条件，或在非搜索旅行场景中生成受限的说明文本。具体航班号、价格、时刻、余票、舱位可用性、详情链接和预订链接必须来自数据库推荐结果。

### 推荐结果可点击

每个推荐航班必须包含：

- 查看详情链接；
- 立即预订链接；
- 查看全部筛选结果链接。

## 3. 用户交互流程

### 场景一：裸目的地澄清

用户输入：

```text
我想去北京
```

系统判断这是目的地意向，但没有明确询问机票、航班、票价、余票、舱位或时刻。

AI 回复：

```text
您是想查询机票，还是想了解目的地/路线的玩法和出行信息？如果要查机票，请告诉我出发城市、目的地和出发日期；想了解目的地也可以直接问。
```

### 场景二：航班查询信息不足

用户输入：

```text
查上海到北京机票
```

系统判断缺少：

- 出发日期。

AI 回复：

```text
请问您的出发日期是什么？
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

### 场景三：信息完整

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
| departureDateStart | 出发日期范围开始 |
| departureDateEnd | 出发日期范围结束 |
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
| 8月2到5号 / 7月6日到7月8日 | departureDateStart + departureDateEnd |
| 我现在在广州 / 人在广州 | departureCity = 广州 |
| 上海迪士尼 / 迪士尼 / 外滩 | arrivalCity = 上海 |

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

LLM 解析是可选增强能力。默认情况下系统继续使用规则解析。

provider 配置有两个来源，优先级为 **管理后台数据库配置（`ai_llm_config` 表）优先 > 环境变量 fallback**：

- 管理员可通过 `GET / PUT /api/admin/ai/llm-config`（仅 ADMIN portal）在运行时查看（apiKey 脱敏，形如 `sk****wxyz`）和修改 provider 配置；apiKey 以 AES-GCM 加密入库、脱敏返回、修改留审计，写入后下一个 AI 请求即生效，无需重启后端。
- 数据库无配置时，回退环境变量 `AI_LLM_ENABLED` / `AI_LLM_BASE_URL` / `AI_LLM_API_KEY` / `AI_LLM_MODEL` / `AI_LLM_TIMEOUT_MS` / `AI_LLM_MAX_RETRIES`（即 `.env` / `application.yml` 默认值）。

无论来源，只有当生效配置 `enabled=true` 且 `baseUrl`、`apiKey`、`model` 均非空时，后端才会优先调用 OpenAI-compatible 接口；否则继续使用规则解析。LLM 调用失败（超时、HTTP 错误、JSON 非法、密钥无法解密等）也会自动降级到规则解析或后端固定模板。

LLM 提示词核心要求：

```text
你是机票查询条件解析器。你只能把用户的购票需求解析成 JSON。
当前日期是 {yyyy-MM-dd}。用户提到“今天、明天、后天、下周”等相对日期时，必须基于这个日期换算。
不要编造航班、价格、库存、机场、航空公司或 URL。
如果缺少必要字段，请在 missingFields 中列出，并给出 followUpQuestion。
```

LLM 输出示例：

```json
{
  "departureCity": "上海",
  "arrivalCity": "北京",
  "departureDate": "2026-05-26",
  "passengerCount": 1,
  "cabinClass": "ECONOMY",
  "airlineRaw": "南方航空",
  "maxPrice": 1200,
  "departureTimeStart": "08:00",
  "departureTimeEnd": "12:00",
  "maxDurationMinutes": 180,
  "directOnly": true,
  "sort": "PRICE_ASC",
  "missingFields": [],
  "followUpQuestion": null,
  "quickActionLabels": []
}
```

当前后端只接受并归一化现有 `ParsedCondition` 支持的字段：

```text
departureCity
arrivalCity
departureDate
departureDateStart
departureDateEnd
passengerCount
cabinClass
airlineRaw
minPrice
maxPrice
departureTimeStart
departureTimeEnd
maxDurationMinutes
directOnly
sort
missingFields
followUpQuestion
quickActionLabels
```

文档早期示例中提到的 `timePreference`、`pricePreference`、`baggageRequired` 等扩展字段当前不进入后端搜索条件。LLM 如果返回未知字段，后端会忽略；如果返回非法日期、非法舱位、非法排序、超出范围的人数或非 JSON 内容，系统会降级为规则解析。

LLM 可用于不确定消息的 domain intent 分类，也可用于问候/旅行建议文本润色。平台帮助事实使用后端固定模板，不使用自由模型知识。非搜索 LLM 文本如果出现航班号、明确价格、URL、座位数、余票或当前可订等具体航班事实，后端会丢弃该文本并回退到安全模板。

LLM 不生成推荐结果。航班号、价格、余票、舱位可用性、详情链接和预订链接仍由 `FlightRecommendationService` 基于数据库航班和座位数据生成。

当 LLM 和规则解析同时参与时，明确日期范围以规则解析结果为准，避免模型把“2到5号这几天”压缩成单个日期。若只有“最近几天”“未来一周”“周一周二都可以”等模糊时间，系统仍要求用户提供一个明确出发日期或明确日期范围。

## 9. 后端处理流程

```text
AiChatController
↓
AiChatService
↓
CompositeIntentParser
├── LlmIntentParserService（配置开启且可用）
└── IntentParserService（默认规则解析和失败降级）
↓
ParsedCondition
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
