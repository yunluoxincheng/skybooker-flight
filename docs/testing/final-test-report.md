# 交付前测试报告

## 1. 当前阶段

当前阶段：测试文档已建立，已开始执行缺陷复现与基线测试。

本报告仍不是最终验收报告，不填写最终通过率，不给出最终交付通过结论。只有完整执行功能、接口、安全、数据一致性、UI/UX、部署、并发和回归测试后，才能更新最终通过率和交付建议。

## 2. 测试依据

| 类型 | 文件 |
|---|---|
| 需求与功能 | `docs/01_REQUIREMENTS.md`、`docs/02_FEATURE_SPEC.md` |
| API 与鉴权 | `docs/07_API_DESIGN.md`、`docs/15_AUTH_DESIGN.md` |
| 前端设计 | `docs/09_FRONTEND_DESIGN.md`、`frontend/DESIGN.md` |
| 后端与状态机 | `docs/10_BACKEND_DESIGN.md`、`docs/16_STATE_MACHINE.md` |
| 部署与测试 | `README.md`、`docs/11_DEPLOYMENT_GUIDE.md`、`docs/12_TESTING_GUIDE.md` |
| 测试数据 | `docs/17_TEST_DATA_GUIDE.md`、`scripts/generate_test_data.py`、`scripts/validate_test_data.py` |
| 历史审计 | `docs/audit/` |

## 3. 本阶段已完成

| 项目 | 结果 |
|---|---|
| 建立测试计划 | 已完成 |
| 建立需求验收清单 | 已完成 |
| 建立功能/接口测试用例 | 已完成 |
| 建立回归测试清单 | 已完成 |
| 建立 UI/UX 清单 | 已完成 |
| 建立安全测试清单 | 已完成 |
| 整理已知缺陷 | 已完成 |
| 纳入 PR #78 测试数据方案 | 已完成 |
| 前端 lint/build 基线 | 已执行，构建通过，lint 有警告 |
| 测试数据生成与静态校验 | 已执行，通过 |
| 后端自动化测试基线 | 已执行，失败，已登记 Issue #85 |
| 后端部署 smoke | 已执行，通过 |
| 浏览器缺陷复现 | 已执行 KI-001 至 KI-009 |
| GitHub 缺陷登记 | 已创建 Issue #79 至 #89 |

## 4. 命令执行记录

| 命令/动作 | 结果 |
|---|---|
| `git status -sb` | 当前分支为 `test/final-system-test...origin/test/final-system-test` |
| `docker compose ps` | `skybooker-backend`、`skybooker-mysql`、`skybooker-nginx`、`skybooker-redis` 均 running/healthy |
| `java -version` | Java 21.0.11 |
| `mvn -version` | Maven 3.8.7 |
| `node -v` | Node v24.16.0 |
| `pnpm -v` | pnpm 11.3.0 |
| `python3 --version` | Python 3.12.3 |
| 检查 `scripts/smoke/backend-smoke.sh` | 文件存在，已纳入测试计划 |
| 检查 `scripts/jmeter/same-seat-order-race.jmx` | 文件存在，已纳入测试计划 |
| 检查 `scripts/concurrency/verify-same-seat-order-race.sh` | 文件存在，已纳入测试计划 |
| `cd frontend && pnpm lint` | 退出码 0；存在 11 条 warning，主要是未使用变量、Hook dependency、React Compiler 与 `watch` 兼容性警告 |
| `cd frontend && pnpm build` | 退出码 0；构建成功 |
| `python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date 2026-07-07` | 生成 `backend/src/main/resources/db/seed/seed-dev.sql`，文件被 `.gitignore` 忽略，不提交 |
| `python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql` | 静态校验通过；生成 336 个航班、14640 个座位、120 个订单等 dev 测试数据 |
| `cd backend && mvn test`（默认测试库） | 失败；本地旧测试库 Flyway V2-V5 checksum mismatch，判定为测试环境陈旧问题 |
| `MYSQL_TEST_DB=flight_booking_test_codex_20260707133006 mvn test`（新测试库） | Flyway 10 个迁移成功；333 个测试中 8 个失败，已登记 Issue #85 |
| `SKYBOOKER_BASE_URL=http://localhost:8088 SKYBOOKER_ADMIN_PASSWORD='SkyBooker@Init2026!' SKYBOOKER_SMOKE_OUTPUT_DIR=reports/smoke/final-system-test-20260707 scripts/smoke/backend-smoke.sh` | 通过；公开航班、用户登录、管理员登录、`/me`、角色隔离、订单列表、AI chat、管理端 dashboard 均符合脚本断言 |
| Playwright 浏览器验证 `http://localhost:8088/` | 返回 gateway 占位页，未提供前端 UI，已登记 Issue #86 |
| Playwright 浏览器验证 `http://localhost:3000` | 已用于 KI-001 至 KI-009 复现；该端口为已有 Next dev server |
| `gh issue list --repo yunluoxincheng/skybooker-flight --state open --limit 50` | 创建前未发现本轮缺陷重复 issue |
| GitHub issue 创建 | 已创建 #79、#80、#81、#82、#83、#84、#85、#86 |
| Playwright 浏览器验证 `/flights` 航班卡片 | 已确认列表卡片只显示起降时间和用时，不显示日期，Issue #87 |
| Playwright 浏览器验证 `/flights/110007` 与 `/waitlist` | 已确认售罄航班缺少候补入口，`/waitlist` 返回 404，Issue #88 |
| Playwright 浏览器验证 `/admin/flights` | 已确认后台无航司/机场管理菜单，新增航班只填写航司 ID 和机场 ID，Issue #89 |
| `curl /api/admin/airlines`、`curl /api/admin/airports` | 均返回 404 `资源不存在`，Issue #89 |
| GitHub issue 创建 | 已追加 #87、#88、#89 |

## 5. 已执行基线结果

### 后端 smoke

| 检查项 | HTTP 结果 | 结论 |
|---|---:|---|
| `GET /api/flights?page=1&size=1` | 200 | 通过 |
| `POST /api/auth/login` | 200 | 通过 |
| `POST /api/admin/auth/login` | 200 | 通过 |
| `GET /api/auth/me`（用户 token） | 200 | 通过 |
| `GET /api/admin/me`（管理员 token） | 200 | 通过 |
| `GET /api/admin/me`（用户 token） | 403 | 通过 |
| `GET /api/auth/me`（管理员 token） | 403 | 通过 |
| `GET /api/orders?page=1&size=5` | 200 | 通过 |
| `POST /api/ai/chat` | 200 | 通过 |
| `GET /api/admin/dashboard/summary` | 200 | 通过 |

### 浏览器主流程抽样

| 流程 | 测试数据 | 实际结果 | 状态 |
|---|---|---|---|
| 普通用户登录 | `user1@example.com / User@123456` | 登录成功，首页 Header 显示用户头像 `S` | 通过 |
| 未来航班下单 | 航班 `110124`，座位 `10A`，乘机人“张三” | `POST /api/orders` 返回 200，订单 `150121` 状态 `PENDING_PAYMENT` | 通过 |
| 模拟支付 | 订单 `150121` | `POST /api/orders/150121/pay` 返回 200，订单状态变为 `ISSUED` | 通过 |
| 过期航班下单 | 航班 `1`，2026-06-18，座位 `1B` | 页面允许走到提交订单，`POST /api/orders` 返回 400 `航班不可预订` | 未通过，Issue #82 |
| 部署首页 | `http://localhost:8088/` | 返回 gateway 占位页，不是前端首页 | 未通过，Issue #86 |
| 航班卡片日期 | `/flights` 首张卡片 | 卡片仅显示 `08:30 / 10:45 / 2时15分`，未显示日期 | 未通过，Issue #87 |
| 售罄航班候补入口 | 航班 `110007 / KE8501` | 可售 0，页面只显示“已售罄”，无候补入口；`/waitlist` 404 | 未通过，Issue #88 |
| 航司/机场管理 | `/admin/flights`、`/api/admin/airlines`、`/api/admin/airports` | 后台无管理入口，接口返回 404，新增航班依赖手工 ID | 未通过，Issue #89 |

## 6. 已知缺陷与 Issue

| 编号 | 摘要 | 严重级别 | 当前状态 | Issue |
|---|---|---|---|---|
| KI-001 | 登录后首页底部仍显示“免费注册”文案 | S3 | 已复现 | #79 |
| KI-002 | 新增乘机人类型选项中英文不一致 | S3 | 已复现 | #80 |
| KI-003 | 新增乘机人表单校验失败时不展示具体错误且按钮卡住 | S1 | 已复现 | #81 |
| KI-004 | 默认列表过期航班仍可预订，提交订单返回 400“航班不可预订” | S0 | 已复现，范围已澄清 | #82 |
| KI-005 | 登录密码无法切换可见性 | S3 | 已复现 | #83 |
| KI-006 | 已出票订单改签按钮固定禁用且规则提示不清 | S2 | 已复现 | #84 |
| BASE-001 | 后端 `mvn test` 在新测试库仍有 8 个失败 | S1 | 已复现 | #85 |
| DEP-001 | docker compose nginx 根路径未提供前端 UI | S1 | 已复现 | #86 |
| KI-007 | 航班卡片未显示出发/到达日期 | S2 | 已复现 | #87 |
| KI-008 | 售罄航班缺少候补入口且 `/waitlist` 页面不存在 | S1 | 已复现 | #88 |
| KI-009 | 后台缺少航司和机场管理接口及页面 | S1 | 已复现 | #89 |

## 7. 尚未执行的测试

| 类别 | 命令或动作 | 状态 |
|---|---|---|
| 完整功能测试 | 用户、航班、订单、支付、AI、管理端所有用例逐条执行 | 未完成 |
| 完整接口异常矩阵 | 缺少参数、非法参数、未登录、权限不足、数据不存在、重复操作 | 未执行 |
| 安全专项 | token 伪造/过期、订单越权、支付/退改签资源归属、XSS/SQL 注入、敏感信息泄露 | 未执行 |
| 数据库一致性 SQL | `docs/17_TEST_DATA_GUIDE.md` 中导入后 SQL 校验 | 未执行 |
| 同座位并发测试 | `scripts/concurrency/verify-same-seat-order-race.sh` / JMeter | 未执行 |
| 管理端完整 CRUD | 用户、航班、订单、AI 配置、统计报表 | 未执行 |
| UI/UX 全页面巡检 | 移动端、空状态、错误页、loading、表单提示、响应式布局 | 部分执行 |
| AI 多轮上下文 | 推荐目的地、条件修改、异常兜底、搜索链接上下文 | 未完整执行 |
| 回归测试 | 修复后主流程和相关模块回归 | 未执行 |

## 8. 高风险区域

- 部署入口：`http://localhost:8088/` 未提供前端 UI，影响交付环境首页验收。
- 后端自动化测试：新测试库 `mvn test` 仍有 8 个失败，且 AI 测试疑似依赖真实 LLM 输出。
- 默认航班列表：过期航班仍被展示为可预订，默认第一条航班可触发下单失败。
- 表单错误处理：新增乘机人表单确认存在 Zod 错误不展示、按钮卡住；需扩展审计其它表单。
- 改签流程：已出票订单前端入口固定禁用，无法验证后端改签规则是否完整可用。
- 候补流程：售罄航班无候补入口，用户端候补页面不存在，候补主功能无法从前端验收。
- 管理端基础资料：航司/机场管理接口和页面缺失，新增航班依赖手工 ID，不满足管理员维护需求。
- 航班列表日期：列表卡片缺少日期，用户难以区分不同日期航班，尤其会放大过期航班展示风险。
- 并发库存：同座位并发脚本存在但尚未执行，座位锁与订单唯一性仍需专项验证。

## 9. 初始建议

建议进入下一阶段代码修复，但修复顺序应聚焦阻塞和高风险项：

1. P0：修复 Issue #82，确保默认航班列表和预订页不会让用户对过期/不可订航班走完整下单流程。
2. P1：修复 Issue #86，确保部署地址能打开真实前端首页。
3. P1：修复 Issue #85，恢复后端自动化测试基线可信度。
4. P1：修复 Issue #81，统一处理表单校验错误展示和按钮 loading 恢复。
5. P1：修复 Issue #88，补齐售罄航班候补入口和“我的候补”页面。
6. P1：修复 Issue #89，补齐航司/机场管理接口和后台页面。
7. P2：修复 Issue #84，明确改签功能状态和业务规则提示。
8. P2：修复 Issue #87，航班卡片补充日期和跨天提示。
9. P3：修复 Issue #79、#80、#83 等 UI/UX 一致性问题。

完成上述修复后，应重新执行 smoke、未来航班下单支付、过期航班拦截、表单校验、部署首页、售罄航班候补、航司/机场管理、后端 `mvn test` 和相关回归测试。
