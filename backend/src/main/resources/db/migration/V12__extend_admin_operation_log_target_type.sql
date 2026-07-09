-- V12: 扩展 admin_operation_log.target_type 枚举
-- 航司/机场删除审计需要 AIRLINE / AIRPORT 两个 target_type。
-- 硬删除策略下不给 airline/airport 加 DELETED 状态(物理删除,不留状态),
-- 因此本迁移只放宽审计表的 target_type CHECK。
ALTER TABLE admin_operation_log DROP CHECK chk_admin_operation_log_target_type;
ALTER TABLE admin_operation_log
    ADD CONSTRAINT chk_admin_operation_log_target_type
    CHECK (target_type IN ('ORDER','USER','AIRLINE','AIRPORT'));
