# 需求验收清单

状态说明：未实际执行的项目统一标记为“未执行”。后续测试时按真实结果更新为“通过”“失败”“阻塞”或“待复现”。

| 编号 | 模块 | 验收项 | 验证方式 | 依据 | 实际结果 | 状态 |
|---|---|---|---|---|---|---|
| REQ-AUTH-001 | 用户认证 | 邮箱验证码注册成功 | 前端表单 + `POST /api/auth/register` | `docs/02_FEATURE_SPEC.md` | 未执行 | 未执行 |
| REQ-AUTH-002 | 用户认证 | 重复邮箱注册失败并提示清楚 | 接口异常 + UI 错误提示 | `docs/07_API_DESIGN.md` | 未执行 | 未执行 |
| REQ-AUTH-003 | 用户认证 | 普通用户可登录用户端 | 前端登录 + `POST /api/auth/login` | `docs/15_AUTH_DESIGN.md` | 未执行 | 未执行 |
| REQ-AUTH-004 | 用户认证 | 管理员不能通过用户端登录 | 接口权限边界 | `docs/15_AUTH_DESIGN.md` | 未执行 | 未执行 |
| REQ-AUTH-005 | 用户认证 | 管理员只能通过管理端登录 | `/admin` + `/api/admin/auth/login` | `docs/15_AUTH_DESIGN.md` | 未执行 | 未执行 |
| REQ-AUTH-006 | 用户认证 | token 过期或无效时清理本地登录态并提示 | 手工构造过期/伪造 token | `docs/15_AUTH_DESIGN.md` | 未执行 | 未执行 |
| REQ-AUTH-007 | 用户认证 | 登录表单缺失或非法输入时展示字段错误，且不发生原生 GET 提交 | `/login` 空密码、空邮箱、非法邮箱浏览器验证 | `docs/09_FRONTEND_DESIGN.md` | 空密码未展示“请输入密码”，空邮箱未展示字段错误；部分场景 URL 出现表单字段，Issue #96 | 失败 |
| REQ-FLT-001 | 航班 | 按日期 + 航班号查询 | `GET /api/flights` | `docs/07_API_DESIGN.md` | 未执行 | 未执行 |
| REQ-FLT-002 | 航班 | 按日期 + 出发地 + 目的地查询 | 前端搜索 + 接口 | `docs/02_FEATURE_SPEC.md` | 未执行 | 未执行 |
| REQ-FLT-003 | 航班 | 筛选航空公司、价格、时间、直飞、舱位、余票 | 前端筛选 + 接口参数 | `docs/02_FEATURE_SPEC.md` | 未执行 | 未执行 |
| REQ-FLT-004 | 航班 | 航班详情显示航司、机场、城市、时间、价格、状态 | `/flights/[id]` | `docs/09_FRONTEND_DESIGN.md` | 详情页基础信息展示正常；列表卡片缺少日期，Issue #87 | 部分通过 |
| REQ-PAX-001 | 乘机人 | 用户可新增成人/儿童/婴儿乘机人 | `/booking/[flightId]` + `/api/passengers` | `docs/02_FEATURE_SPEC.md` | 未执行 | 未执行 |
| REQ-PAX-002 | 乘机人 | 用户只能管理自己的乘机人 | 接口归属校验 | `docs/07_API_DESIGN.md` | 未执行 | 未执行 |
| REQ-ORD-001 | 订单 | 选择航班、乘机人、座位后可创建订单 | `POST /api/orders` | `docs/02_FEATURE_SPEC.md` | 未执行 | 未执行 |
| REQ-ORD-002 | 订单 | 重复乘机人、重复座位提交失败 | 接口异常用例 | `docs/07_API_DESIGN.md` | 未执行 | 未执行 |
| REQ-ORD-003 | 订单 | 用户只能查看自己的订单 | 接口归属校验 | `docs/15_AUTH_DESIGN.md` | 未执行 | 未执行 |
| REQ-PAY-001 | 支付 | 待支付订单可模拟支付为已出票 | `POST /api/orders/{id}/pay` | `docs/16_STATE_MACHINE.md` | 未执行 | 未执行 |
| REQ-PAY-002 | 支付 | 重复支付不产生重复售票或状态错乱 | 重复请求 + 数据库校验 | `docs/16_STATE_MACHINE.md` | 未执行 | 未执行 |
| REQ-PAY-003 | 支付 | 超时订单不能支付 | 构造过期待支付订单 | `docs/16_STATE_MACHINE.md` | 未执行 | 未执行 |
| REQ-REF-001 | 退票 | 已出票订单可退票并计算手续费 | `POST /api/orders/{id}/refund` | `docs/02_FEATURE_SPEC.md` | 未执行 | 未执行 |
| REQ-REF-002 | 退票 | 起飞前不足 2 小时不可退票且提示明确 | 边界数据验证 | `docs/16_STATE_MACHINE.md` | 未执行 | 未执行 |
| REQ-CHG-001 | 改签 | 已出票订单可查询同航线改签候选 | `GET /api/orders/{id}/change-options` | `docs/07_API_DESIGN.md` | 未执行 | 未执行 |
| REQ-CHG-002 | 改签 | 起飞前不足 2 小时不可改签且提示明确 | 边界数据验证 | `docs/16_STATE_MACHINE.md` | 未执行 | 未执行 |
| REQ-WL-001 | 候补 | 目标舱位无票时可提交候补 | `POST /api/waitlist` | `docs/02_FEATURE_SPEC.md` | 售罄航班 `110007` 无前端候补入口，`/waitlist` 404，Issue #88 | 失败 |
| REQ-WL-002 | 候补 | 退票释放座位后按规则兑现候补 | 集成流程 + 数据库校验 | `docs/16_STATE_MACHINE.md` | 未执行 | 未执行 |
| REQ-AI-001 | AI 助手 | 可解析“明天广州到上海的便宜航班”并返回真实航班 | `POST /api/ai/chat` | `docs/08_AI_CUSTOMER_SERVICE.md` | 未执行 | 未执行 |
| REQ-AI-002 | AI 助手 | 缺少日期时追问，不直接查单日 | 多轮对话 | `docs/12_TESTING_GUIDE.md` | 未执行 | 未执行 |
| REQ-AI-003 | AI 助手 | 修改条件后上下文正确更新 | 多轮对话 | `docs/12_TESTING_GUIDE.md` | 未执行 | 未执行 |
| REQ-ADM-001 | 管理端 | 管理员可进入看板、用户、航班、订单、报表页面 | 浏览器验证 | `docs/09_FRONTEND_DESIGN.md` | 未执行 | 未执行 |
| REQ-ADM-002 | 管理端 | 普通用户禁止访问 `/api/admin/**` | 接口权限边界 | `docs/15_AUTH_DESIGN.md` | 未执行 | 未执行 |
| REQ-ADM-003 | 管理端 | 航班管理支持舱位配置、发布、下架、生成座位 | 管理端页面 + 接口 | `docs/07_API_DESIGN.md` | 未执行 | 未执行 |
| REQ-ADM-004 | 管理端 | 航司和机场可由管理员维护 | 管理端页面 + `/api/admin/airlines`、`/api/admin/airports` | `docs/01_REQUIREMENTS.md` | 后台无航司/机场管理入口；两个接口均返回 404；后端 #90，前端 #91 | 失败 |
| REQ-DEP-001 | 部署 | README 可指导本地启动 | 从空环境按 README 执行 | `README.md` | 未执行 | 未执行 |
| REQ-DEP-002 | 部署 | 空库启动后 Flyway 可完成 schema 初始化 | MySQL 空库 + 后端启动 | `docs/11_DEPLOYMENT_GUIDE.md` | 未执行 | 未执行 |
| REQ-DATA-001 | 测试数据 | `generate_test_data.py` 可生成 dev/test seed | 生成 + 静态校验 | `docs/17_TEST_DATA_GUIDE.md` | 未执行 | 未执行 |
| REQ-DATA-002 | 测试数据 | 导入 seed 后一致性 SQL 返回空结果 | 数据库校验 | `docs/17_TEST_DATA_GUIDE.md` | 未执行 | 未执行 |
