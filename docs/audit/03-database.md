# 数据库层审查报告

- **维度**:SQL 注入、表结构/约束/索引、Flyway 迁移、软删除、金额/时间、枚举一致性
- **审查方**:database-reviewer
- **日期**:2026-06-24

审查对象:V1-V6 迁移、10 个 Mapper XML、实体/Mapper 接口。依据 `docs/06_DATABASE_DESIGN.md`、`appendices/sql-convention.md`、`docs/16_STATE_MACHINE.md`。

## CRITICAL

未发现。`${orderBy}` 虽为字符串拼接,但经 `FlightService.sortToOrderBy()`(FlightService.java:84-93)和 `FlightRecommendationService.resolveOrderBy()`(:98-109)由 `FlightSort` 枚举硬编码白名单产出,用户 `sort` 只参与枚举选择、不直入 SQL,注入被白名单挡住,安全可控。

## HIGH

### H1. 表结构缺字符集声明,存在中文乱码风险
- **证据**:`V1__init_schema.sql` 全部 `CREATE TABLE`、V2-V6 均无 `DEFAULT CHARSET=utf8mb4`。JDBC URL 用 `characterEncoding=utf8`(application.yml:6),但库/表字符集取决于 MySQL server 默认值。
- **风险**:server 默认 `latin1` 时,中文城市名/姓名写入或迁移回滚后可能乱码;跨环境不一致。
- **建议**:后续新建表统一 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`;确认 server `character_set_server=utf8mb4`。

### H2. 缺少 CHECK 约束,状态枚举完全依赖应用层
- **证据**:V1 各表 `status VARCHAR(30)` 无 CHECK(`ticket_order.status`、`flight_seat.status`、`waitlist_order.status`、`users.role`/`status` 等)。
- **风险**:脏数据(如 `status='PAID'`、role 非法值)绕过应用层直写时无法拦截;MySQL 8.0.16+ 已支持 CHECK。
- **建议**:对核心状态字段补 CHECK 约束(新 V 脚本),覆盖订单/座位/候补/角色枚举值,与应用层枚举对齐。

### H3. 外键缺 `ON DELETE` 行为,删除会失败或留孤儿
- **证据**:除 `flight_seat.locked_by_order_id`(SET NULL)和 `waitlist_passenger.locked_seat_id`(SET NULL)外,其余 FK 均**无 ON DELETE**(默认 RESTRICT)。如 `ticket_order` → `users`/`flight`、`order_passenger` → `ticket_order`/`flight_seat`、`refund_record`/`change_record` → `ticket_order`。
- **风险**:删除航班/用户被 FK 阻止;或临时禁用 FK 留孤儿。`06_DATABASE_DESIGN.md:40-47` 要求"数据库负责保证引用完整性"。
- **建议**:明确每个 FK 删除语义(航班/用户软删除而非物理删除;审计表 `refund_record`/`change_record` 显式 `ON DELETE RESTRICT`)。

## MEDIUM

- **M1** 全库无 `deleted_at`,无软删除列;`passenger.deleteById` 硬删除(`PassengerMapper.xml:28-30`)。建议统一软删除策略或文档化"硬删除+快照"取舍。
- **M2** 分页用 OFFSET(`LIMIT #{offset}, #{size}`),深翻性能差(FlightMapper/OrderMapper/AuthMapper)。增长型表改游标分页。
- **M3** `auth_verification_code_log` 无二级索引,防刷查询将全表扫。预加 `INDEX(target, created_at)`、`INDEX(ip_address, created_at)`。
- **M4** 仪表盘 `AdminMapper.selectDashboardSummary` 对 ticket_order/waitlist_order/refund_record 全表 COUNT/SUM 无时间范围。加时间范围或缓存。
- **M5** `ticket_order.flight_id` 改签后指向新航班,旧航班维度丢失(`OrderMapper.updateOrderFlightAndAmounts`)。报表层确认是否 JOIN `change_record` 还原历史归属。
- **M6** `order_passenger`/`refund_record`/`change_record`/`auth_verification_code_log`/`ai_chat_message`/`ai_recommendation_record` 缺 `updated_at`(违反 `appendices/sql-convention.md:25`)。

## LOW

- **L1** `flight.punctuality_rate DEFAULT 95.00` 等业务魔数落在 DDL(V1:107)→ 移应用层常量。
- **L2** `users.password_hash` 在 `findById` 等多处 SELECT 返回 → 鉴权外查询不选 password_hash。
- **L3** `findMessagesBySessionId` 无 LIMIT,长会话内存压力(AiMapper.xml:26-31)。
- **L4** Flyway V1-V6 命名/顺序合规,未发现编辑已应用迁移;`scripts/refresh-demo-flight-dates.sql` 是手工脚本,注意隔离。
- **L5** 金额 DECIMAL(10,2) 正确;时间 DATETIME + `serverTimezone=Asia/Shanghai` 正确。跨时区部署再评估。

## 未发现

- **N+1**:列表查询均 JOIN 一次性取齐;`findByUserId` 用 resultMap collection,无循环单查。
- **批量插入**:`batchInsertFlightSeats`/`batchInsertOrderPassengers`/`batchInsertWaitlistPassengers` 已用 `<foreach>`。
- **乐观锁**:`flight_seat.version` 已建且更新带 `version = version + 1`。
- **座位唯一约束**:`uk_flight_seat(flight_id, seat_no)` 已建。
- **候补排序索引**:`idx_waitlist_flight_status_paid(flight_id, status, paid_at, id)` 覆盖 `ORDER BY paid_at, id`。
- **核心索引齐全**:外键列均有索引,与文档建议一致。

## 数据库层总体评估

设计**整体扎实**:索引覆盖到位、乐观锁与状态机字段合理、批量插入与 FK 规范、SQL 注入通过白名单枚举有效阻断(无实际注入风险)。主要短板在**约束强度偏弱**(无 CHECK、FK 缺 ON DELETE、字符集未显式声明)和**删除策略未统一**(全硬删除、审计表缺 updated_at)。当前演示规模不暴露,向真实/长期演进时建议优先补齐 H1(字符集)、H2(CHECK)、H3(FK 删除行为),并为防刷日志表与增长型订单表预留索引与游标分页。
