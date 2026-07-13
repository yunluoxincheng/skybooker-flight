# scripts

## 三步快速刷新

本地 Docker 环境推荐按 `doctor → status → seed → validate --database → status` 执行。`seed` 会自动替换同 profile 的旧 ownership 数据，不需要先 `clean`：

```bash
docker compose up -d mysql redis
./scripts/test-data.sh doctor --dir "$PWD" --source-dir "$PWD"
./scripts/test-data.sh status --dir "$PWD" --source-dir "$PWD"
./scripts/test-data.sh seed --dir "$PWD" --source-dir "$PWD" --profile dev --yes
./scripts/test-data.sh validate --dir "$PWD" --source-dir "$PWD" \
  --file "$PWD/test-data/seed-dev.sql" --database
./scripts/test-data.sh status --dir "$PWD" --source-dir "$PWD"
```

服务器没有仓库克隆时，下载入口并固定到 commit SHA：

```bash
curl -fsSL \
  https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/main/scripts/test-data.sh \
  -o /tmp/skybooker-test-data.sh
bash /tmp/skybooker-test-data.sh seed \
  --dir /opt/skybooker --repo yunluoxincheng/skybooker-flight \
  --ref <commit-sha> --profile dev --yes
bash /tmp/skybooker-test-data.sh validate \
  --dir /opt/skybooker --repo yunluoxincheng/skybooker-flight \
  --ref <commit-sha> --file /opt/skybooker/test-data/seed-dev.sql --database
```

`clean` 只删除该 profile 的 ownership 数据；`docker compose down -v` 会删除整个数据库 volume、手工数据和 Flyway 历史，不能把它当作普通清理命令。

该目录用于放置项目辅助脚本，例如：

- 本地启动脚本；
- 数据库重置脚本；
- Docker 构建脚本；
- 演示数据刷新脚本。

当前提供：

- `test-data.sh`：测试数据统一入口，编排 doctor/generate/validate/import/seed/clean/status，并支持本地仓库或按固定 ref 下载运行。
- `generate_test_data.py`：按 dev/test/perf profile、components 和 scenarios 生成可复现 seed SQL，加载 `data/` 下的机场/航司目录，覆盖联程航班、订单和改签快照。
- `data/`：可审查的机场和航司基础目录；机场当前包含 260 个中国大陆机场、15 个港澳台机场和 22 个国际枢纽，并在 JSON metadata 中记录来源和更新时间。
- `validate_test_data.py`：按 seed summary 动态静态校验，也可执行数据库级一致性校验。
- `clean_test_data.py`：生成 profile 范围内的安全清理 SQL，不删除航司/机场、默认账号或其他 profile。
- `refresh-demo-flight-dates.sql`：历史兼容占位；Flyway 不再自动插入演示航班，演示日期请通过重新生成 seed 刷新。
- `seed-connecting-itineraries.sql`：历史兼容脚本，联程数据已统一由 `test-data.sh` 生成；新验收不要再使用该脚本。
- `smoke/backend-smoke.sh`：部署后烟测脚本，检查公共航班查询、用户/管理员登录边界、用户订单、AI 聊天和管理员统计。
- `jmeter/same-seat-order-race.jmx`：JMeter 同座位并发下单测试计划。
- `jmeter/run-same-seat-concurrency-report.sh`：JMeter 同座位并发报告运行脚本，生成时间戳证据目录、HTML 报告、运行日志、数据库校验输出和中文摘要。
- `jmeter/same-seat-concurrency-report-template.md`：同座位并发测试中文报告摘要模板。
- `jmeter/README.md`：JMeter 测试数据准备、报告运行命令、证据目录和期望结果。
- `concurrency/verify-same-seat-order-race.sh`：并发测试后的数据库校验脚本，验证目标座位只有一行绑定且只绑定一个订单。

生成的烟测日志、JMeter `.jtl`、HTML 报告和截图统一放在 `reports/` 下，默认不提交到 Git。
JMeter 自动生成的 HTML 页面可能包含工具自带英文界面文字；仓库提交的说明文档和正式摘要报告使用中文。

## 测试数据统一入口

本地仓库运行：

```bash
./scripts/test-data.sh doctor --dir "$PWD" --source-dir "$PWD"
./scripts/test-data.sh seed --dir "$PWD" --source-dir "$PWD" --profile dev --scenarios all --yes
./scripts/test-data.sh validate --dir "$PWD" --source-dir "$PWD" --file "$PWD/test-data/seed-dev.sql" --database
./scripts/test-data.sh status --dir "$PWD" --source-dir "$PWD"
./scripts/test-data.sh clean --dir "$PWD" --source-dir "$PWD" --profile dev --yes
```

只生成或导入指定模块/场景：

```bash
./scripts/test-data.sh generate \
  --profile test \
  --components flights,orders,refunds,changes \
  --scenarios direct,connecting,refund,change \
  --output /tmp/seed-test.sql

./scripts/test-data.sh validate --file /tmp/seed-test.sql
./scripts/test-data.sh import --file /tmp/seed-test.sql --yes
```

可选组件为 `reference,users,flights,orders,refunds,changes,waitlists,ai,all`；可选场景为 `direct,connecting,payment,cancel,refund,change,waitlist,sold-out,delayed,near-departure,all`。未显式传 `--scenarios` 时默认生成所选组件范围内适用的全部场景，不会反向扩大组件；显式传入场景后才会补齐场景依赖，例如 `orders` 自动包含用户、乘机人、航班、舱位和座位，`refund` 自动包含 `orders,refunds`。需要严格检查依赖时使用 `--no-auto-dependencies`，缺少场景组件会明确报错。

参考机场目录对所有 profile 完整导入；profile 只控制产生航班的机场数量：dev 选 24 个，test/perf 选完整目录。航线生成先保证每个选中机场至少一条进港和出港航线，再补齐国内枢纽双向航线及国际/特殊地区到中国大陆枢纽的连接。`validate` 和 `validate --database` 都会检查这些覆盖指标。

没有仓库克隆的部署机可以下载入口。`--ref` 会同时约束生成器、校验器和清理器的版本，避免旧后端误用 `main` 上的新数据结构：

```bash
curl -fsSL \
  https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/main/scripts/test-data.sh \
  -o /tmp/skybooker-test-data.sh
bash /tmp/skybooker-test-data.sh status \
  --dir /opt/skybooker \
  --repo yunluoxincheng/skybooker-flight \
  --ref <commit-sha>
```

入口会优先读取部署目录 `.env` 和 Compose MySQL 容器；宿主机 MySQL 可通过 `--host --port --user --db` 或同名环境变量连接，Docker 不存在时不影响宿主机回退。密码只通过环境变量/容器环境传递，不写入日志。所有写入要求 Flyway V18+；生产标记环境还需要 `--allow-production --confirm-production --yes`。`seed`、`import` 和 `clean` 默认要求确认，CI 使用 `--yes`。

## 兼容的底层脚本

生成和校验：

```bash
python3 scripts/generate_test_data.py --profile dev --seed 20260707 --scenarios all
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql

python3 scripts/generate_test_data.py --profile test --seed 20260707
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-test.sql

# 性能测试数据规模较大，仅在压测环境生成
python3 scripts/generate_test_data.py --profile perf --seed 20260707
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-perf.sql
```

Docker Compose MySQL 导入：

```bash
docker exec -i skybooker-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-flight_booking}"' \
  < backend/src/main/resources/db/seed/seed-dev.sql
```

导入 perf 数据时把文件换成 `seed-perf.sql`。`perf` 只用于性能测试或压测环境，不建议导入日常开发库，也不要提交生成后的 `seed-perf.sql` 到 Git。

完整说明见 `docs/17_TEST_DATA_GUIDE.md`。

`refresh-demo-flight-dates.sql` 现在只保留为兼容占位。Flyway 不再自动插入演示航班，刷新演示日期请重新生成 seed SQL。
