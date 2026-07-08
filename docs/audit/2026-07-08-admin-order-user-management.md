# 管理端订单与普通用户维护 — 变更审计

- **审查日期**:2026-07-08
- **变更**:`add-admin-order-user-management`
- **范围**:管理端订单维护、普通用户维护、`admin_operation_log` 审计、`VOIDED` / `DELETED` 状态落库

## 变更概览

| 维度 | 实现 |
|---|---|
| 订单维护 | 管理员代下单、退票、改签、作废、更新 `admin_note`、查询退改记录 |
| 用户维护 | 管理员新增普通用户、逻辑删除、禁用、启用 |
| 状态扩展 | `ticket_order.status = VOIDED`、`users.status = DELETED` |
| 迁移 | V11 新增 `admin_operation_log`、`ticket_order.admin_note`，重建订单和用户状态 CHECK |
| 审计 | 订单/用户维护写操作写入 `admin_operation_log` |

## 安全与一致性要点

### 1. 管理端权限边界

所有新增接口都位于 `/api/admin/**`，由现有 ADMIN portal 鉴权规则保护。普通用户 Token 返回 403，匿名请求返回 401。

### 2. 复用核心业务逻辑

管理端代下单复用普通下单的锁座、金额、余票扣减和乘客快照逻辑；管理端退票和改签复用普通流程的 CAS 状态推进、座位释放、余票维护、候补兑现和费用计算。`force=true` 只绕过 2 小时窗口限制，费用按不足 24 小时档 30% 计算。

### 3. 逻辑删除与作废

用户删除使用 `status = DELETED`，不物理删除历史业务行。`DELETED` 为终态，启用接口只允许 `DISABLED -> NORMAL`。订单作废只允许 `CANCELLED` / `REFUNDED -> VOIDED`，不修改库存或支付、退改签记录。

### 4. 审计范围

审计覆盖代下单、管理员退票、管理员改签、作废、备注更新、用户新增、用户删除、用户禁用和用户启用。失败操作不写成功审计；幂等退票重复请求不重复写审计。审计表只存操作元数据，不存密码哈希、Token 或密钥。

## 测试验证

| 测试类 | 用例数 | 覆盖 |
|---|---:|---|
| `AdminIntegrationTest` | 33 | 管理端订单/用户维护、权限边界、CHECK 落库、审计、force 退改、作废、逻辑删除 |

全套件结果以本变更最终 `mvn test` 记录为准。

## 相关文档

- API 契约:`docs/07_API_DESIGN.md` §10
- 功能说明:`docs/02_FEATURE_SPEC.md` §12
- 数据库设计:`docs/06_DATABASE_DESIGN.md`
- 状态机:`docs/16_STATE_MACHINE.md`
- 错误码:`appendices/error-code.md`
