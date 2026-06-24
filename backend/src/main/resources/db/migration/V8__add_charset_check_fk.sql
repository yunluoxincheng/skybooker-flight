-- ============================================================
-- V8: Database hardening (审查 H13)
-- a) utf8mb4 字符集(所有表,避免 server 默认 latin1 中文乱码)
-- b) CHECK 约束(状态/角色枚举字段,值对照代码 + 16_STATE_MACHINE)
-- c) FK ON DELETE 语义(order_passenger -> CASCADE)
-- 枚举值已 grep 代码确认(2026-06-24),后续新增状态需同步本约束
-- ============================================================

-- H13a: utf8mb4
ALTER TABLE users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE admin_user CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE auth_verification_code_log CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE oauth_account CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE passenger CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE airline CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE airport CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE flight CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE flight_seat CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE ticket_order CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE order_passenger CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE refund_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE change_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE waitlist_order CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE waitlist_passenger CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE ai_chat_session CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE ai_chat_message CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE ai_recommendation_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- H13b: CHECK constraints
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('USER','ADMIN'));
ALTER TABLE users ADD CONSTRAINT chk_users_status CHECK (status IN ('NORMAL','DISABLED'));
ALTER TABLE admin_user ADD CONSTRAINT chk_admin_user_status CHECK (status IN ('ENABLED','DISABLED'));
ALTER TABLE airline ADD CONSTRAINT chk_airline_status CHECK (status IN ('ENABLED','DISABLED'));
ALTER TABLE airport ADD CONSTRAINT chk_airport_status CHECK (status IN ('ENABLED','DISABLED'));
ALTER TABLE flight ADD CONSTRAINT chk_flight_status CHECK (status IN ('ON_TIME','DELAYED','CANCELLED'));
ALTER TABLE flight ADD CONSTRAINT chk_flight_publish_status CHECK (publish_status IN ('PUBLISHED','DRAFT'));
ALTER TABLE flight_seat ADD CONSTRAINT chk_flight_seat_status CHECK (status IN ('AVAILABLE','LOCKED','SOLD','DISABLED'));
ALTER TABLE ticket_order ADD CONSTRAINT chk_ticket_order_status CHECK (status IN ('PENDING_PAYMENT','ISSUED','CANCELLED','REFUNDED','CHANGED','CHANGE_PENDING'));
ALTER TABLE waitlist_order ADD CONSTRAINT chk_waitlist_order_status CHECK (status IN ('PENDING_PAYMENT','WAITING','SUCCESS','EXPIRED','FAILED','CANCELLED','REFUNDED'));
ALTER TABLE auth_verification_code_log ADD CONSTRAINT chk_auth_log_send_status CHECK (send_status IN ('SUCCESS','FAILED'));
ALTER TABLE ai_chat_session ADD CONSTRAINT chk_ai_chat_session_status CHECK (status IN ('ACTIVE','DELETED'));
ALTER TABLE ai_chat_message ADD CONSTRAINT chk_ai_chat_message_role CHECK (role IN ('USER','ASSISTANT','SYSTEM'));
ALTER TABLE ai_chat_message ADD CONSTRAINT chk_ai_chat_message_type CHECK (message_type IN ('TEXT','RECOMMENDATION'));

-- H13c: FK ON DELETE
-- order_passenger 是订单专属乘客快照,删订单应级联删快照(其余 FK 保持默认 RESTRICT 防误删;flight_seat.locked_by_order 已是 SET NULL)
ALTER TABLE order_passenger DROP FOREIGN KEY fk_order_passenger_order;
ALTER TABLE order_passenger ADD CONSTRAINT fk_order_passenger_order FOREIGN KEY (order_id) REFERENCES ticket_order(id) ON DELETE CASCADE;
