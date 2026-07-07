-- 最小默认账号数据。
-- 管理员初始密码会在 V7 中轮换为 SkyBooker@Init2026!；普通用户密码为 User@123456。
-- 航司、机场、航班、座位和业务场景数据不再由 Flyway 自动初始化，统一使用 db/seed 或 scripts 生成导入。
INSERT INTO users(email, phone, password_hash, nickname, real_name, role, email_verified) VALUES
('admin@skybooker.local', NULL, '$2b$12$1nvx/dJbiwwV6AckTZq.KeJlLyLnsjo0y9UkfKrdcdOQCqVJwRA1S', '系统管理员', '系统管理员', 'ADMIN', 1),
('user1@example.com', '13800000000', '$2b$12$mxGK3588bVIlwCYgjrqa1.1esZ8vbAKALvroPmpAvJfGt3VO781oy', 'SkyBooker演示用户', '演示用户', 'USER', 1);

INSERT INTO admin_user(user_id, username, job_no, real_name, remark) VALUES
(1, 'admin', 'ADMIN001', '系统管理员', 'Flyway 初始化管理员扩展资料');

INSERT INTO passenger(user_id, name, id_card_no, passenger_type, phone) VALUES
(2, '张三', '110101199001010011', 'ADULT', '13800000000');
