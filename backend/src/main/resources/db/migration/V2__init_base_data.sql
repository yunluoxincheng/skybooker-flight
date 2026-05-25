-- 演示默认密码：管理员 Admin@123456，普通用户 User@123456。首次部署后必须修改默认密码。
INSERT INTO users(email, phone, password_hash, nickname, real_name, role, email_verified) VALUES
('admin@skybooker.local', NULL, '$2b$12$1nvx/dJbiwwV6AckTZq.KeJlLyLnsjo0y9UkfKrdcdOQCqVJwRA1S', '系统管理员', '系统管理员', 'ADMIN', 1),
('user1@example.com', '13800000000', '$2b$12$mxGK3588bVIlwCYgjrqa1.1esZ8vbAKALvroPmpAvJfGt3VO781oy', 'SkyBooker演示用户', '演示用户', 'USER', 1);

INSERT INTO admin_user(user_id, username, job_no, real_name, remark) VALUES
(1, 'admin', 'ADMIN001', '系统管理员', 'Flyway 初始化管理员扩展资料');

INSERT INTO passenger(user_id, name, id_card_no, passenger_type, phone) VALUES
(2, '张三', '110101199001010011', 'ADULT', '13800000000');

INSERT INTO airline(code, name) VALUES
('MU', '东方航空'),
('CZ', '南方航空'),
('CA', '中国国航'),
('HU', '海南航空');

INSERT INTO airport(code, name, city, province) VALUES
('SHA', '上海虹桥国际机场', '上海', '上海'),
('PVG', '上海浦东国际机场', '上海', '上海'),
('PEK', '北京首都国际机场', '北京', '北京'),
('PKX', '北京大兴国际机场', '北京', '北京'),
('CAN', '广州白云国际机场', '广州', '广东'),
('SZX', '深圳宝安国际机场', '深圳', '广东'),
('CTU', '成都双流国际机场', '成都', '四川');
