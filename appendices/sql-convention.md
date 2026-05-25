# SQL 与 Flyway 规范

## 1. Flyway 命名

格式：

```text
V版本号__说明.sql
```

示例：

```text
V1__init_schema.sql
V2__init_base_data.sql
```

注意版本号和说明之间是两个下划线。

## 2. 脚本规则

- 已经执行到团队环境的 V 脚本不要修改；
- 新增变更使用新的 V 脚本；
- 表名使用小写下划线；
- 字段名使用小写下划线；
- 每张表包含 `created_at` 和 `updated_at`；
- 金额使用 `DECIMAL(10,2)`；
- 状态字段使用 `VARCHAR`，便于阅读；
- 强身份关系建议使用外键约束，例如 `admin_user.user_id` 关联 `users.id`；复杂业务条件如 `users.role = ADMIN` 由 Service 层校验。

## 3. 字段建议

```sql
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

## 4. 索引建议

高频查询字段需要加索引：

- 航班路线和日期；
- 航班号；
- 座位状态；
- 用户订单；
- 候补队列。
