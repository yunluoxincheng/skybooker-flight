# 10_BACKEND_DESIGN：后端工程设计

## 1. 后端定位

后端负责所有核心业务逻辑，包括认证、航班查询、订票、库存控制、退改签、候补、AI 推荐和后台管理。

## 2. 技术栈

```text
Spring Boot
Spring MVC
MyBatis
MySQL
Redis
Flyway
Spring Security
JWT
Validation
Knife4j / Swagger
```

## 3. 分层结构

每个业务模块内部采用：

```text
controller
service
mapper
entity
dto
vo
enums
```

职责：

| 层 | 职责 |
|---|---|
| Controller | 接收请求、参数校验、返回响应 |
| Service | 业务逻辑、事务控制 |
| Mapper | 数据库访问 |
| Entity | 数据库实体 |
| DTO | 请求参数 |
| VO | 响应对象 |
| Enums | 状态枚举 |

## 4. 认证与权限

使用 Spring Security + JWT。

用户登录成功后返回 Token。

前端请求时携带：

```http
Authorization: Bearer <token>
```

权限：

```text
/api/admin/** 仅 ADMIN 可访问，`POST /api/admin/auth/login` 除外
/api/orders/** 仅 USER 可访问
/api/passengers/** 仅 USER 可访问
/api/waitlist/** 仅 USER 可访问
/api/flights/** 公开访问
/api/ai/** 匿名或 USER 可访问，ADMIN 不作为用户端身份使用
```

账号统一存储在 `users` 表中，通过 `role` 字段区分普通用户和管理员：

```text
USER  普通用户
ADMIN 管理员
```

登录入口按端隔离：

```text
POST /api/auth/login             用户端登录，只允许 role = USER
POST /api/admin/auth/login       管理端登录，只允许 role = ADMIN
GET  /admin                      前端页面路由，管理后台登录页或后台首页入口
```

用户端登录使用邮箱和密码，若账号 `role = ADMIN`，返回无权限或账号类型不匹配。管理端登录使用 `admin_user.username` 和密码，后端关联 `users` 表校验 `password_hash`、`users.status`、`admin_user.status` 和 `role = ADMIN`，若账号不是管理员则拒绝登录。

后端从 JWT 中解析 `userId`、`email`、`role` 和 `loginPortal`，构造 `LoginUserPrincipal`：

```java
public record LoginUserPrincipal(
    Long userId,
    String email,
    String role,
    String loginPortal
) {}
```

权限判断方式：

- Spring Security 将 `role = ADMIN` 映射为 `ROLE_ADMIN`；
- `/api/admin/**` 使用 `hasRole("ADMIN")` 或等价配置保护；
- 公开放行必须使用精确匹配：只放行 `POST /api/admin/auth/login`。`GET /admin` 是前端页面路由，不应作为后端 API 放行规则的一部分；
- 用户端受保护接口要求 `role = USER`，避免管理员 Token 访问用户端订单、乘机人等个人业务接口；
- Service 层如需二次校验，统一读取 `LoginUserPrincipal`，不要直接信任前端传入的 `userId` 或 `role`；
- `/api/admin/users` 只操作 `role = USER` 的普通用户账号，列表默认过滤管理员账号，启用和禁用操作必须拒绝 `role = ADMIN` 账号以及当前登录管理员自身；
- `admin_user` 保存管理员用户名和扩展资料，不保存密码哈希，密码仍以 `users.password_hash` 为准。

## 5. MyBatis 设计

### Mapper 接口

```java
@Mapper
public interface FlightMapper {
    List<FlightListVO> searchFlights(FlightSearchDTO dto);
}
```

### XML 动态 SQL

航班查询条件较多，适合使用 MyBatis XML。

```xml
<select id="searchFlights" resultType="FlightListVO">
    SELECT ...
    FROM flight f
    JOIN airline a ON f.airline_id = a.id
    WHERE f.publish_status = 'PUBLISHED'
    <if test="departureCity != null">
        AND dep.city = #{departureCity}
    </if>
    <if test="arrivalCity != null">
        AND arr.city = #{arrivalCity}
    </if>
</select>
```

## 6. 事务设计

需要使用事务的方法：

- 创建订单并锁定座位；
- 支付订单并更新座位为已售；
- 取消订单并释放座位；
- 退票并释放座位；
- 退票后触发候补锁座；
- 改签释放旧座位并锁定新座位。

事务实现要求：

- 创建订单时先校验乘机人归属和座位归属，再创建订单主表和明细；
- 多座位锁定必须按 `seat_id` 升序处理，降低死锁概率；
- 每个座位使用条件更新从 `AVAILABLE` 改为 `LOCKED`；
- 任意一个座位锁定失败时抛出业务异常，整个事务回滚，订单和已锁座位都不能残留；
- 锁座成功后同步扣减 `flight.remaining_seats`，扣减条件必须保证余票不少于本次乘机人数；
- 支付、取消、退票、候补过期和改签都必须校验当前状态，重复请求要返回当前最终结果，不能重复扣减或释放。
- 候补支付成功时必须创建正式订单，并把正式订单 ID 写回 `waitlist_order.ticket_order_id`。

创建订单伪流程：

```text
读取当前用户
校验 flight 可售
校验 items 非空、乘机人不重复、座位不重复
校验乘机人属于当前用户
校验座位属于目标航班
创建 ticket_order，状态 PENDING_PAYMENT
按 seat_id 升序逐个条件更新座位 AVAILABLE -> LOCKED
若任一座位锁定失败，回滚事务
写入 order_passenger 快照
扣减 flight.remaining_seats
返回订单
```

## 7. 并发订票设计

使用数据库条件更新 + version 乐观锁。

```sql
UPDATE flight_seat
SET status = 'LOCKED',
    version = version + 1,
    locked_by_order_id = #{orderId},
    lock_expire_time = #{lockExpireTime}
WHERE id = #{seatId}
  AND status = 'AVAILABLE'
  AND version = #{version}
```

影响行数为 1：成功。

影响行数为 0：失败，座位已被占用。

扣减航班余票必须同样使用条件更新：

```sql
UPDATE flight
SET remaining_seats = remaining_seats - #{count}
WHERE id = #{flightId}
  AND remaining_seats >= #{count};
```

如果影响行数为 0，说明余票缓存已经不足或发生并发冲突，必须回滚订单创建事务。

## 8. 订单超时释放

基础版可以在用户取消或支付失败时释放。

增强版可以使用定时任务：

```text
每分钟扫描 PENDING_PAYMENT 且超过 15 分钟的订单
↓
订单改为 CANCELLED
↓
座位 LOCKED 改为 AVAILABLE
```

释放座位时必须清空 `locked_by_order_id` 和 `lock_expire_time`，并把 `version` 加 1。订单超时释放后需要同步增加 `flight.remaining_seats`。

支付接口必须校验：

- 订单属于当前用户；
- 订单状态为 `PENDING_PAYMENT`；
- 当前时间未超过 `expire_time`；
- 订单明细中的全部座位仍为 `LOCKED`；
- 每个座位的 `locked_by_order_id` 都等于当前订单 ID。

只有全部条件满足时，才能把订单改为 `ISSUED`，并把座位改为 `SOLD`。

## 9. AI 助手后端设计

模块：

```text
ai/controller/AiChatController
ai/service/AiChatService
ai/service/IntentParserService
ai/service/FlightRecommendationService
ai/mapper/AiChatMapper
```

处理流程：

```text
接收用户消息
↓
创建或加载 AI 会话，外部 sessionId 映射到 ai_chat_session.public_session_id
↓
保存用户消息
↓
解析意图
↓
检查缺失字段
↓
查询航班
↓
生成回复
↓
保存 AI 回复和推荐记录
```

AI 会话实现要求：

- `sessionId` 是 `ai_chat_session.public_session_id`，不暴露数据库自增主键；
- 匿名会话 `user_id = NULL`，依赖不可猜测的 `sessionId` 继续会话；
- 登录普通用户会话写入 `user_id`，读取、追加消息、删除时必须校验会话归属；
- 管理员 Token 不能访问 `/api/ai/**`。

## 10. 异常处理

使用全局异常处理：

```text
GlobalExceptionHandler
```

常见异常：

- 参数校验失败；
- 未登录；
- 无权限；
- 航班不存在；
- 座位已售；
- 库存不足；
- 订单状态不允许操作；
- AI 解析失败。

## 11. 配置文件示例

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/flight_booking?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: change-me-change-me-change-me
  expiration: 86400000
```

## 认证模块设计

认证模块包结构建议：

```text
auth/
├── controller/
│   └── AuthController.java
├── service/
│   ├── AuthService.java
│   ├── EmailCodeService.java
│   └── MailService.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── SecurityConfig.java
├── dto/
│   ├── SendEmailCodeDTO.java
│   ├── RegisterDTO.java
│   ├── LoginDTO.java
│   └── ResetPasswordDTO.java
└── vo/
    └── LoginVO.java
```

核心设计：

- 密码使用 BCrypt 哈希存储；
- 注册、找回密码验证码存储在 Redis；
- Redis Key 示例：`auth:email-code:register:{email}`；
- 验证码默认 5 分钟有效；
- 登录成功后签发 JWT；
- 管理员账号不开放注册；
- 手机验证码和第三方登录作为扩展能力预留。

邮件服务实现建议：

- `MockMailService`：开发环境使用，在控制台打印验证码；
- `SmtpMailService`：演示或部署环境使用 SMTP 发送邮件；
- 后续可扩展 `BrevoMailService`、`ResendMailService`。
