# AI LLM 后台配置 — 变更安全审查

- **审查日期**:2026-06-26
- **变更**:`add-ai-llm-admin-config`(分支 `feature/ai-admin-config`)
- **范围**:AI LLM provider 配置的运行时管理 —— DB 优先 + 环境变量 fallback + 管理后台 `GET/PUT /api/admin/ai/llm-config`
- **方法**:变更落地后专项安全审查 + 测试验证

## 变更概览

| 维度 | 实现 |
|---|---|
| 配置表 | `ai_llm_config` 单行表(V9 迁移,utf8mb4 + 列级 CHECK,固定 id=1 upsert) |
| 配置来源优先级 | DB 记录 > 环境变量 fallback(`AiLlmProperties`) |
| 动态刷新 | `DynamicLlmConfigProvider` TTL 5s 缓存 + 写后清缓存 |
| per-request 一致性 | 入口读一次 `LlmEffectiveConfig`,沿调用链显式传参(无 ThreadLocal) |
| 加密 | `LlmConfigCrypto` AES-256-GCM,独立 `AI_CONFIG_ENC_KEY` |

## 安全设计要点

### 1. apiKey 加密存储(防 DB 泄露)
- 算法 AES-256-GCM(带认证),IV 随密文存储,编码 `base64(iv):base64(cipher+tag)`。
- 密钥来自独立环境变量 `AI_CONFIG_ENC_KEY`(base64 32 字节),**不复用 `JWT_SECRET`**(职责隔离)。
- DB `api_key_cipher` 仅存密文;GET 永不返回明文。

### 2. 鉴权(防越权)
- `/api/admin/ai/llm-config` 由 `SecurityConfig` 的 `/api/admin/**` 通配收敛为 `requireAdminPortal`(role=ADMIN + loginPortal=ADMIN 双校验)。
- 普通用户 token → 403;匿名 → 401(经 `SecurityExceptionHandler`)。

### 3. 脱敏(防 shoulder-surfing / 前端日志泄露)
- GET 返回 `sk****wxyz`(前2+****+末4);过短统一 `****`;env apiKey 空时返回空串不脱敏。

### 4. 修改审计
- `updated_by`(admin_user.id)+ `updated_at` 落库。
- INFO 日志记录"管理员 id=X 更新了 AI LLM 配置(enabled=..., model=...)",**不记 apiKey 内容**。
- PUT 可选 apiKey:省略/传 null = 保留旧密文;传空白 = 拒绝(10022)。

### 5. 降级语义(不 crash、不阻断 AI 对话)
- `AI_CONFIG_ENC_KEY` 缺失/格式非法 → 应用正常启动,走 env fallback;仅 PUT 时返回 `AI_LLM_CONFIG_INVALID(10022)` 不落库。
- DB 有记录但解密失败 → fallback env,不抛异常。
- DB 故障 → fallback env,不阻断聊天(与 LLM 失败 fallback 规则一致)。

### 6. per-request 配置一致性
- `CompositeIntentParser.parse` 入口读一次 cfg,显式传给 `LlmIntentParserService.parse(msg, cfg)` → `LlmChatClient.complete(system, user, cfg)`。
- 避免单次请求跨越 TTL 边界读到不一致配置;后台 PUT 后下一个请求生效新值,正在处理的请求用旧值完成。

## 校验规则(PUT 失败统一 10022 / 400)

- `enabled=true` 但 `baseUrl` / `model` 为空,或提交新 apiKey 但为空白。
- `timeout_ms <= 0` 或 `max_retries < 0`(DB 列级 CHECK 兜底)。
- crypto 不可用(加密密钥未配置)。

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| 加密密钥丢失 → DB 密文不可解密 | 自动 fallback env;部署指南提示备份 `AI_CONFIG_ENC_KEY` |
| 多实例缓存不一致 | 最多滞后 1 TTL(5s);如需立即可后续引入 Redis pub/sub(YAGNI,暂不做) |
| 首次 PUT 省略 apiKey | 加密空串占位保证 `api_key_cipher NOT NULL`;enabled=true 时省略 key 会被校验拒绝 |

## 测试验证

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `DynamicLlmConfigProviderTest` | 6 | DB 优先 / DB 空 fallback / TTL 缓存命中 / 写后清缓存 / 解密失败降级 / crypto 不可用降级 |
| `AdminAiConfigIntegrationTest` | 8 | GET env-default 脱敏 / PUT 生效+脱敏 / apiKey 加密落库 / 省略 apiKey 保留旧密文 / 启用缺字段 10022 / 超时范围 10022 / USER 403 / 匿名 401 |
| `LlmIntentParserServiceTest` | 11 | 接口签名适配(per-request cfg 传参)后全绿 |
| `AiLlmIntegrationTest` | 4 | `@MockBean LlmChatClient` stub 签名适配后全绿 |

全套件 `mvn test` 结果以 8.x 阶段执行为准。

## 相关文档

- 提案:`openspec/changes/add-ai-llm-admin-config/`(本地,不入库)
- 配置来源说明:`docs/08_AI_CUSTOMER_SERVICE.md` §8
- 部署与密钥管理:`docs/11_DEPLOYMENT_GUIDE.md` §3
- 错误码:`appendices/error-code.md`(`AI_LLM_CONFIG_INVALID` 10022)
