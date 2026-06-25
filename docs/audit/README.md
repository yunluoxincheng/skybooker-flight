# 后端深度审查报告

> **📌 修复状态(2026-06-25):全部 CRITICAL + HIGH 已处理**(修复或评估关闭)。详见 [修复状态总览](#修复状态总览)。以下为审查时点(2026-06-24)的原始记录,保留作回溯。

- **审查日期**:2026-06-24
- **审查对象**:backend 全模块(11 模块 / 122 生产类 / 23 测试类;前端未实现,不在范围)
- **方法**:4 路并行专项 subagent + 基线 `mvn test`
- **基线**:`mvn test` BUILD SUCCESS(全绿)——所有发现均为代码现状问题,非测试已有失败

## 修复状态总览

> 更新于 2026-06-25。**全部 CRITICAL(5) + HIGH(15) 已处理**(修复或评估关闭)。MEDIUM/LOW 维持 P2 排期未处理。

- **修复 PR**:#28(C1–C5)、#30(H6/H9/H11/H14/H15)、#31(H13 DB V8)、#32(H8/H10/H12)、#33(H3/H4)、#34(死代码清理)
- **未单独修**:H1(lockSeats version)——并发已由 `status='AVAILABLE'`→`LOCKED` CAS + C1 兜底覆盖,`version` 字段冗余未入 WHERE,可后续优化
- **评估闭环**:H7(管理员禁用自身)——改用 ADMIN 角色整体保护(`ADMIN_ACCOUNT_PROTECTED`),强于"仅禁止禁用自身"

逐项映射与闭环方式见 [汇总报告 · 修复状态总览](2026-06-24-backend-deep-review.md#修复状态总览)。

## 报告索引

| 文件 | 维度 | CRITICAL | HIGH |
|---|---|---|---|
| [汇总报告](2026-06-24-backend-deep-review.md) | 去重整合 + 修复优先级 | 5 | ~13 |
| [01-security.md](01-security.md) | 安全:认证/授权/注入/限流/敏感信息 | 1 | 5 |
| [02-concurrency-transaction.md](02-concurrency-transaction.md) | 并发/状态机/事务边界 | 4 | 6 |
| [03-database.md](03-database.md) | SQL/表结构/索引/Flyway 迁移 | 0 | 3 |
| [04-doc-conformance.md](04-doc-conformance.md) | 文档符合性/接口契约/测试覆盖 | 2 | 4 |

> 分维度报告保留各审查方的原始发现(含证据与建议);汇总报告为去重整合后的统一视图,以它为准做修复排期。

## 总体结论

**BLOCK**——不建议在修复前直接进入完整测试流程。5 个 CRITICAL 必须先修复:

1. 订单状态机非原子(`payOrder`/`cancelOrder` 无 CAS)
2. 候补兑现事务设计错误(同事务污染 + CAS 顺序倒置 + 余票延迟扣减)
3. 默认管理员弱口令哈希入库 + 登录无限流
4. 错误码体系全面偏离 `appendices/error-code.md`
5. 退票接口缺 `reason` 请求体(与 `07_API_DESIGN` 不符)

详见 [汇总报告](2026-06-24-backend-deep-review.md)。
