# 交付前测试报告

## 1. 当前阶段

当前阶段：测试文档建立完成，尚未进入完整执行阶段。

本报告目前是初始报告模板，不填写最终通过率，不给出最终交付结论。只有在实际执行测试并记录证据后，才能更新执行结果、通过率和交付建议。

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

## 4. 命令执行记录

以下为本阶段可记录的准备性只读核对。完整测试命令尚未执行。

| 命令/动作 | 结果 |
|---|---|
| `git status -sb` | 已确认当前 checkout 为 `main...origin/main` |
| `git log --oneline --decorate --all --grep='78\|#78\|PR#78\|pull request #78' -n 20` | 已确认 PR #78 合并提交为 `2920167` |
| 检查 `scripts/smoke/backend-smoke.sh` | 文件存在 |
| 检查 `scripts/jmeter/same-seat-order-race.jmx` | 文件存在 |
| 检查 `scripts/concurrency/verify-same-seat-order-race.sh` | 文件存在 |
| 阅读 `docs/17_TEST_DATA_GUIDE.md` | 已确认测试数据生成、校验、导入和覆盖场景 |
| 阅读 `scripts/generate_test_data.py` / `scripts/validate_test_data.py` | 已确认生成/校验参数和默认输出 |
| `find docs/testing -maxdepth 1 -type f | sort` | 已确认 8 个测试文档存在 |
| `rg -n "[ \t]+$" docs/testing || true` | 未发现新增测试文档尾随空白 |
| `rg` 检查状态措辞和禁用表述 | 未发现未执行用例被误写为通过；仅命中“不得填写最终结论”的说明文本 |
| `python3 scripts/generate_test_data.py --help` | 已确认生成脚本参数可用，未生成 seed 文件 |
| `python3 scripts/validate_test_data.py --help` | 已确认校验脚本参数可用，未执行 seed 校验 |

## 5. 尚未执行的测试

| 类别 | 命令或动作 | 状态 |
|---|---|---|
| 后端自动化测试 | `cd backend && mvn test` | 未执行 |
| 前端 lint | `cd frontend && pnpm lint` | 未执行 |
| 前端构建 | `cd frontend && pnpm build` | 未执行 |
| 测试数据生成 | `python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date 2026-07-07` | 未执行 |
| 测试数据静态校验 | `python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql` | 未执行 |
| 数据库一致性校验 | `docs/17_TEST_DATA_GUIDE.md` 中 SQL | 未执行 |
| 部署 smoke | `SKYBOOKER_BASE_URL=http://localhost:8088 scripts/smoke/backend-smoke.sh` | 未执行 |
| 同座位并发测试 | `scripts/jmeter/same-seat-order-race.jmx` | 未执行 |
| 浏览器端到端验证 | 登录、搜索、下单、支付、订单、AI、管理端 | 未执行 |

## 6. 已知缺陷汇总

| 编号 | 摘要 | 严重级别 | 当前状态 |
|---|---|---|---|
| KI-001 | 登录后首页底部仍显示“免费注册”文案 | S3 | 待复现确认 |
| KI-002 | 新增乘机人类型选项中英文不一致 | S3 | 待复现确认 |
| KI-003 | 多处表单校验失败时不展示具体错误且提交按钮卡住 | S1 | 待复现确认 |
| KI-004 | 提交订单返回 400“航班不可预订”，阻塞核心下单流程 | S0 | 待复现确认 |
| KI-005 | 登录密码无法切换可见性 | S3 | 待复现确认 |
| KI-006 | 改签不可用或“起飞前 2 小时不可改签”规则提示不清 | S2 | 待复现确认 |

## 7. 高风险区域

- 核心下单链路：`POST /api/orders` 返回“航班不可预订”如复现成立，将阻塞交付主流程。
- 表单错误处理：多处表单若出现校验失败不提示、按钮卡住，会显著影响可用性。
- 订单/支付/退改签状态机：需要重点验证状态推进、座位状态和余票一致性。
- 角色访问边界：普通用户、管理员、未登录用户必须符合 `docs/15_AUTH_DESIGN.md`。
- 测试数据一致性：生成 seed 后必须通过静态校验和数据库级一致性 SQL。

## 8. 初始建议

当前不建议直接给出最终交付结论。建议下一阶段先执行测试数据生成与导入、后端/前端基础命令、smoke 和 `KI-004` 复现。

如果 `KI-004` 复现成立，建议优先进入代码修复阶段；否则继续执行完整功能、接口、UI/UX、安全、数据一致性和回归测试。
