# 前端 Issue 清单 — AI 助手回复类型契约（2026-07-02）

> 来源：后端 OpenSpec 变更 `enhance-ai-travel-assistant-backend`
> 范围：该后端变更引入的**需前端后续对齐**的 AI 助手渲染契约。后端实现不修改 `frontend/`。
> 优先级：P0=阻断/核心体验 / P1=明显坏体验 / P2=契约/功能 / P3=低优先级

---

## F-AI-REPLY-1 ｜ AI 助手新增回复类型与 intent 元数据需前端渲染对齐

- **优先级**：P2
- **现象**：后端 AI 助手不再把所有非完整航班查询都当作 `FOLLOW_UP`。问候、旅行建议、平台帮助和越界问题会返回新的 `replyType` 与 `intent`，当前前端若只识别旧类型，可能出现展示样式不准、误渲染缺失字段、快捷操作行为不一致或重复展示追问文本。
- **后端变更 / 证据**：
  - 新增 `replyType`：`CHAT_REPLY`、`TRAVEL_ADVICE`、`PLATFORM_HELP`、`OUT_OF_SCOPE`。
  - 保留既有 `replyType`：`FOLLOW_UP`、`FLIGHT_RECOMMENDATION`、`NO_RESULT`。
  - 新增 `intent` 元数据：`GREETING`、`TRAVEL_ADVICE`、`PLATFORM_HELP`、`FLIGHT_SEARCH`、`FLIGHT_SEARCH_CONTINUATION`、`OUT_OF_SCOPE`。
  - 非搜索回复仍返回标准字段：`parsedCondition` 为空对象、`missingFields` 为空数组、`followUpQuestion = null`、`searchUrl = null`、`flights = []`。
  - 快捷操作继续是文本建议契约：每项为 `{label, value}`；跳转仍使用 `searchUrl`、航班卡片 `detailUrl` / `bookingUrl`。
- **建议修复**：
  1. 前端 AI 类型定义补齐所有 `replyType` 和可选 `intent`。
  2. `CHAT_REPLY`、`TRAVEL_ADVICE`、`PLATFORM_HELP`、`OUT_OF_SCOPE` 渲染为普通助手文本，不展示缺失字段 UI，不渲染航班卡片。
  3. `FOLLOW_UP` 只使用 `replyText` 或 `followUpQuestion` 中一个作为追问文本，避免重复展示。
  4. 快捷操作按 `{label, value}` 作为"发送给助手的文本建议"处理，不在 quick action 内引入跳转 payload。
  5. 导航继续读取顶层 `searchUrl`，以及航班卡片内 `detailUrl` / `bookingUrl`。
- **验证**：
  - 输入"你好"：显示普通助手介绍，不出现出发地/目的地/日期缺失提示。
  - 输入"北京有什么好玩"：显示旅行建议，不显示航班卡片或搜索按钮。
  - 输入"退票怎么操作"：显示平台帮助文本。
  - 输入"帮我写代码"：显示越界拒绝文本。
  - 输入不完整航班搜索后再回复"明天"：仍能按 `FLIGHT_SEARCH_CONTINUATION` 合并上下文。

---

## 排期建议

| Issue | 优先级 | 工作量 | 备注 |
|---|---|---|---|
| F-AI-REPLY-1 | P2 | 小 | 类型 + AI 消息渲染分支 + 快捷操作处理 |
