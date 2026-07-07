# 交付前完整测试计划

## 1. 测试目标

本计划用于 SkyBooker 交付前完整软件测试阶段，目标是在不修改业务代码的前提下，建立可执行、可追踪、可复现的测试体系。

当前阶段重点是测试准备：整理测试范围、测试清单、测试用例、测试数据计划和已知缺陷。除非实际执行并记录证据，否则所有用例状态默认保持为“未执行”。

## 2. 测试范围

| 模块 | 覆盖内容 | 依据 |
|---|---|---|
| 用户认证 | 注册、登录、邮箱验证码、JWT、刷新 token、token 过期、用户信息获取 | `docs/02_FEATURE_SPEC.md`、`docs/07_API_DESIGN.md`、`docs/15_AUTH_DESIGN.md` |
| 航班 | 航班搜索、条件筛选、详情、座位图、舱位、价格、时间、城市、日期展示 | `docs/02_FEATURE_SPEC.md`、`docs/07_API_DESIGN.md` |
| 乘机人 | 新增、修改、删除、归属校验、类型显示 | `docs/02_FEATURE_SPEC.md`、`docs/07_API_DESIGN.md` |
| 订单/支付 | 创建订单、查询订单、订单详情、取消订单、模拟支付、重复提交、重复支付、状态一致性 | `docs/02_FEATURE_SPEC.md`、`docs/16_STATE_MACHINE.md` |
| 退改签 | 退票手续费、退款状态、改签候选航班、改签窗口、改签后座位和金额一致性 | `docs/02_FEATURE_SPEC.md`、`docs/16_STATE_MACHINE.md` |
| 候补 | 候补提交、候补支付、排队、兑现、取消、退款 | `docs/02_FEATURE_SPEC.md`、`docs/16_STATE_MACHINE.md` |
| AI 助手 | 自然语言意图解析、推荐目的地/航班、上下文保留、条件修改、异常提示 | `docs/08_AI_CUSTOMER_SERVICE.md`、`docs/12_TESTING_GUIDE.md` |
| 管理端 | 管理员权限、用户管理、航班管理、订单管理、看板和报表、AI 配置 | `docs/07_API_DESIGN.md`、`docs/09_FRONTEND_DESIGN.md` |
| 前端 UI/UX | 首页、认证页、航班页、预订页、订单页、AI 助手、管理端、错误/空/加载状态、移动端 | `docs/09_FRONTEND_DESIGN.md`、`frontend/DESIGN.md` |
| 部署与文档 | README、环境变量、Flyway、测试数据、构建命令、空库启动、烟测 | `README.md`、`docs/11_DEPLOYMENT_GUIDE.md`、`docs/17_TEST_DATA_GUIDE.md` |

## 3. 严重级别

| 级别 | 标准 |
|---|---|
| S0 阻塞 | 系统无法启动、主流程完全不可用、无法登录、无法下单、严重数据损坏 |
| S1 严重 | 核心功能错误、安全越权、支付/订单状态错误、用户数据泄露 |
| S2 一般 | 非核心功能异常、部分筛选错误、提示不准确、普通页面交互问题 |
| S3 轻微 | 样式细节、文案、间距、图标、轻微体验问题 |

## 4. 测试环境准备

### 4.1 分支与代码状态

- 目标测试分支：`test/final-system-test`。
- 实施前应确认本地分支与预期一致，避免将测试文档落到错误分支。
- 当前测试数据方案来自 PR #78 合并提交，包含 `scripts/generate_test_data.py`、`scripts/validate_test_data.py` 和 `docs/17_TEST_DATA_GUIDE.md`。

### 4.2 基础服务

```bash
docker compose up -d mysql redis
cd backend && mvn spring-boot:run
```

前端本地运行：

```bash
cd frontend && pnpm install && pnpm dev
```

完整部署或演示环境优先参考 `README.md` 和 `docs/11_DEPLOYMENT_GUIDE.md`。

## 5. 测试数据计划

测试数据以 `docs/17_TEST_DATA_GUIDE.md` 为准，使用“真实基础数据 + 规则生成业务数据”的方式准备。`seed-*.sql` 是本地生成产物，默认被 `.gitignore` 忽略，不提交到仓库。

### 5.1 数据 profile

| profile | 用途 | 规模 |
|---|---|---|
| `dev` | 本地开发、课堂演示、小规模手工测试 | 24 个机场、13 家航司、90 条航线组合、未来 7 天、32 个用户、120 个订单 |
| `test` | 功能测试、集成测试、报表和查询验证 | 60 个机场、24 家航司、320 条航线组合、未来 30 天、240 个用户、2200 个订单 |
| `perf` | 性能测试扩展入口 | 仅压测环境生成和导入 |

### 5.2 生成与校验

```bash
python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date 2026-07-07
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql

python3 scripts/generate_test_data.py --profile test --seed 20260707 --base-date 2026-07-07
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-test.sql
```

导入 MySQL 示例：

```bash
docker exec -i skybooker-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-flight_booking}"' \
  < backend/src/main/resources/db/seed/seed-dev.sql
```

### 5.3 必备测试数据类型

| 数据类型 | 准备要求 | 来源 |
|---|---|---|
| 测试账号 | 普通用户、多个 seed 用户、启用/禁用状态 | Flyway 默认用户 + generated seed |
| 管理员账号 | 管理员登录、管理端 token、权限边界 | Flyway 默认管理员，交付环境需改默认密码 |
| 测试航班 | 可预订、未发布、已取消、延误、即将起飞、售罄、跨天到达 | generated seed |
| 测试乘机人 | 成人、儿童、婴儿、重复证件、非当前用户乘机人 | generated seed + 手工补充 |
| 可预订座位 | `AVAILABLE`，覆盖经济舱/公务舱/头等舱 | generated seed |
| 不可预订座位/航班 | `LOCKED`、`SOLD`、`DISABLED`、未发布/已起飞/取消航班 | generated seed |
| 改签边界数据 | 已出票订单、同航线候选航班、起飞前不足 2 小时、超过 24 小时 | generated seed |
| 支付/订单数据 | 待支付、已支付、支付失败/超时、已取消、已退票、已改签 | generated seed |
| 候补数据 | 未支付、等待中、成功、失败、取消、退款 | generated seed |
| AI 数据 | 可查询航班、上下文消息、AI 推荐记录 | generated seed |

### 5.4 数据一致性校验

导入后至少执行 `docs/17_TEST_DATA_GUIDE.md` 中的数据库级校验 SQL，确认：

- `flight.remaining_seats` 与可用座位数一致；
- `flight.total_seats` 与舱位总座位数一致；
- `flight_seat.price` 与对应舱位价格一致；
- `SOLD` 座位有订单乘机人绑定；
- `LOCKED` 座位有锁定订单和过期时间。

## 6. 计划内验证命令

这些命令允许执行，但执行结果必须真实记录到 `final-test-report.md`：

```bash
cd backend && mvn test
cd frontend && pnpm lint
cd frontend && pnpm build
SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh
```

已确认存在并纳入计划的脚本：

- `scripts/smoke/backend-smoke.sh`
- `scripts/jmeter/same-seat-order-race.jmx`
- `scripts/concurrency/verify-same-seat-order-race.sh`

同座位并发测试按 `scripts/jmeter/README.md` 执行，证据目录默认写入 `reports/`，不提交 Git。

## 7. 准入与准出标准

### 准入

- 后端、前端、MySQL、Redis 可以启动；
- 测试数据已按 `docs/17_TEST_DATA_GUIDE.md` 生成、校验并导入；
- 测试账号、管理员账号、航班、座位、订单和候补数据可定位；
- `known-issues.md` 已登记当前已知缺陷。

### 准出

当前阶段不做最终交付结论。完整测试执行阶段完成后，才允许给出通过率和交付建议。

进入代码修复阶段的建议条件：

- 复现任一 S0 缺陷；
- 出现安全越权、订单/支付状态错误、数据一致性错误；
- 主流程烟测无法通过；
- 前端核心表单错误导致用户无法继续操作。
