# 17_TEST_DATA_GUIDE：测试数据生成与初始化

## 1. 目标

SkyBooker 的测试数据采用“真实基础数据 + 规则生成业务数据”：

- 机场和航空公司使用真实或接近真实的 IATA 代码、城市和中文名称；
- 航线不单独建表，按当前数据库设计体现为 `flight.departure_airport_id` + `flight.arrival_airport_id` 的组合；
- 航班、舱位、座位、订单、退票、改签、候补和 AI 会话由脚本按固定规则生成；
- 不依赖实时外部 API，不保存真实用户隐私信息；
- 普通用户测试账号只生成 `nickname` 作为展示名称，不生成已移除的 `users.real_name` 字段；乘机人证件姓名仍写入 `passenger.name`；
- 同一组 `profile + seed + base-date` 可重复生成相同 SQL。

Flyway 迁移只保留 schema、默认管理员、默认普通用户和默认乘机人。航司、机场、航班、座位和业务场景数据不再默认随应用启动自动导入，避免大规模测试数据进入生产环境。

## 2. 统一管理入口

`scripts/test-data.sh` 是生成、校验、导入、状态查看和清理的统一入口。它可以在仓库内运行，也可以下载到只有部署产物的服务器上运行：

```bash
./scripts/test-data.sh doctor --dir /opt/skybooker
./scripts/test-data.sh seed --profile dev --scenarios all --yes
./scripts/test-data.sh validate --file /opt/skybooker/test-data/seed-dev.sql --database
./scripts/test-data.sh status --dir /opt/skybooker
./scripts/test-data.sh clean --profile dev --yes
```

远程入口必须固定 `--ref`，确保入口下载的生成器、校验器、清理器与当前后端版本一致：

```bash
curl -fsSL https://raw.githubusercontent.com/yunluoxincheng/skybooker-flight/main/scripts/test-data.sh \
  -o /tmp/skybooker-test-data.sh
bash /tmp/skybooker-test-data.sh status \
  --dir /opt/skybooker \
  --repo yunluoxincheng/skybooker-flight \
  --ref main
```

公共参数包括 `--dir`、`--repo`、`--ref`、`--source-dir`、`--profile`、`--components`、`--scenarios` 和 `--yes`。组件为 `reference,users,flights,orders,refunds,changes,waitlists,ai,all`；场景为 `direct,connecting,payment,cancel,refund,change,waitlist,sold-out,delayed,near-departure,all`。默认自动补齐依赖，使用 `--no-auto-dependencies` 可改为缺依赖即报错。

`doctor` 检查 Python、Docker Compose、目标 MySQL 容器、连接和 Flyway 版本；`generate` 不访问数据库；`validate` 支持静态和数据库级一致性校验；`seed` 是生成、校验、导入的一键流程；`clean` 只删除目标 profile 的固定 ID 区间。`seed`、`import` 和 `clean` 默认需要确认，自动化环境必须显式传 `--yes`。入口不会执行 `docker compose down -v`、`TRUNCATE` 或关闭外键检查。

部署目录存在 `.env` 和 Compose 文件时，数据库连接优先使用它们；宿主机 MySQL 可通过 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_DB` 或对应 CLI 参数连接。密码不会拼接进日志。

## 3. 数据集分层

`seed-*.sql` 是本地生成产物，不提交到仓库。仓库只保留生成脚本、校验脚本和 `db/seed/` 目录占位文件。

| profile | 用途 | 当前规模 |
|---|---|---|
| dev | 本地开发、课堂演示、小规模手工测试 | 24 个机场、13 家航司、90 条航线组合、未来 7 天、32 个用户、120 个订单 |
| test | 功能测试、集成测试、报表和查询验证 | 60 个机场、24 家航司、320 条航线组合、未来 30 天、240 个用户、2200 个订单 |
| perf | 性能测试扩展入口 | 脚本支持生成，但文件较大，建议在压测环境本地生成后导入 |

seed 使用固定 ID 区间，重复导入时只清理对应区间内的 seed 数据：

| profile | seed ID 区间 |
|---|---|
| dev | `100000` - `199999` |
| test | `200000` - `1199999` |
| perf | `2000000` - `11999999` |

基础航司和机场通过 `code` 幂等 upsert，不使用清理区间删除，便于 dev/test 共用基础航空资料。

## 4. 生成数据

```bash
# 生成开发数据（推荐统一入口）
./scripts/test-data.sh generate --profile dev --seed 20260707 --scenarios all

# 底层生成器仍可直接调用
python3 scripts/generate_test_data.py --profile dev --seed 20260707 --scenarios all

# 生成测试数据
python3 scripts/generate_test_data.py --profile test --seed 20260707

# 生成性能测试数据，规模较大，仅用于压测环境
python3 scripts/generate_test_data.py --profile perf --seed 20260707

# 指定航班基准日期，适合演示前刷新“明天/未来 7 天/未来 30 天”
python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date 2026-07-07

# 生成到自定义路径
python3 scripts/generate_test_data.py --profile test --seed 20260707 --output /tmp/seed-test.sql

# 只生成航班、订单和联程改签相关数据；依赖会自动补齐
./scripts/test-data.sh generate --profile dev \
  --components flights,orders,changes \
  --scenarios direct,connecting,refund,change \
  --output /tmp/connecting.sql
```

默认 `--seed 20260707` 会把 `base-date` 解析为 `2026-07-07`。如果希望演示数据始终从当天开始，显式传入新的 `--base-date`。

## 5. 校验数据

生成后先做静态校验：

```bash
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-dev.sql
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-test.sql
python3 scripts/validate_test_data.py --file backend/src/main/resources/db/seed/seed-perf.sql

# 导入后执行数据库级一致性校验
./scripts/test-data.sh validate --file backend/src/main/resources/db/seed/seed-dev.sql --database
```

校验器会检查：

- seed summary 是否存在；
- profile 规模是否在预期范围；
- 航班、舱位、座位、订单、候补和 AI 核心表是否有插入语句；
- 订单、座位、候补关键状态是否覆盖；
- 生成业务数据 ID 是否都落在当前 profile 的 seed ID 区间；
- 生成器写入的一致性标志是否全部为 true。

导入 MySQL 后建议再执行数据库级校验：

```sql
SELECT f.id
FROM flight f
WHERE f.remaining_seats <> (
  SELECT COUNT(*)
  FROM flight_seat s
  WHERE s.flight_id = f.id AND s.status = 'AVAILABLE'
);

SELECT f.id
FROM flight f
WHERE f.total_seats <> (
  SELECT COALESCE(SUM(c.total_seats), 0)
  FROM flight_cabin c
  WHERE c.flight_id = f.id
);

SELECT s.id
FROM flight_seat s
JOIN flight_cabin c
  ON c.flight_id = s.flight_id AND c.cabin_class = s.cabin_class
WHERE s.price <> c.price;

SELECT s.id
FROM flight_seat s
LEFT JOIN order_passenger op
  ON op.order_id = s.locked_by_order_id AND op.seat_id = s.id
WHERE s.status = 'SOLD' AND op.id IS NULL;

SELECT s.id
FROM flight_seat s
WHERE s.status = 'LOCKED'
  AND (s.locked_by_order_id IS NULL OR s.lock_expire_time IS NULL);
```

这些查询应返回空结果。

## 6. 导入数据

先启动 MySQL，并确保后端已通过 Flyway 初始化 schema：

```bash
docker compose up -d mysql redis
cd backend && mvn spring-boot:run
```

Docker Compose MySQL 导入示例：

```bash
docker exec -i skybooker-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-flight_booking}"' \
  < backend/src/main/resources/db/seed/seed-dev.sql

# 推荐使用统一入口，自动读取部署目录 .env/Compose 配置
./scripts/test-data.sh import --dir /opt/skybooker \
  --file /opt/skybooker/test-data/seed-dev.sql --yes
```

宿主机 MySQL 客户端导入示例：

```bash
mysql --default-character-set=utf8mb4 \
  -h 127.0.0.1 -P 3306 -u root -p flight_booking \
  < backend/src/main/resources/db/seed/seed-dev.sql
```

导入 test 数据只需把文件换成 `seed-test.sql`。dev 和 test 使用不同 ID 区间，可以同时存在；通常建议一个库只导入一种 profile，避免测试结果混杂。

导入 perf 数据时把文件换成 `seed-perf.sql`：

```bash
docker exec -i skybooker-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-flight_booking}"' \
  < backend/src/main/resources/db/seed/seed-perf.sql
```

`perf` 数据规模较大，只用于性能测试或压测环境，不建议导入日常开发库，也不要提交生成后的 `seed-perf.sql` 到 Git。

## 7. 重置数据

重新导入同一个 seed 文件会先清理该 profile 的 seed ID 区间，再重新插入数据。也可以单独安全清理：

```bash
python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date 2026-07-07
docker exec -i skybooker-mysql sh -c \
  'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "${MYSQL_DATABASE:-flight_booking}"' \
  < backend/src/main/resources/db/seed/seed-dev.sql

./scripts/test-data.sh clean --profile dev --yes
```

这不会删除任意用户手工创建的数据，但会删除 seed ID 区间内的数据。需要完全清空本地库时，建议重建数据库或删除本地 Docker volume 后重新跑 Flyway。

`scripts/refresh-demo-flight-dates.sql` 已保留为兼容占位，不再批量改写所有航班日期。演示前需要刷新日期时，请重新生成 seed。

## 8. 覆盖场景

航班查询：

- 广州到上海在同一天有多班，覆盖不同时间和不同价格；
- 热门航线每天多班，普通/冷门航线按较低频率生成；
- 包含直飞和非直飞；
- 包含余票充足、只剩 1 张票、售罄、取消、延误、即将起飞、跨天到达；
- 包含经济舱售罄但公务舱有票、所有舱位售罄。

订单：

- `PENDING_PAYMENT`、`ISSUED`、`CANCELLED`、`REFUNDED`、`CHANGE_PENDING`、`CHANGED`；
- 单乘机人和多乘机人订单；
- 锁座未支付、过期未支付、已退票退款、已改签差价记录；
- 临近起飞航班用于退改窗口边界验证。

座位：

- `AVAILABLE`、`LOCKED`、`SOLD`、`DISABLED`；
- 多个座位被同一待支付订单锁定；
- `flight.remaining_seats` 与 `flight_seat.status = 'AVAILABLE'` 数量一致；
- `flight_cabin.total_seats` 之和等于 `flight.total_seats`；
- `flight_seat.price` 与对应舱位价格一致。

候补：

- 未支付候补、已支付排队中、候补成功、候补失败、候补取消、候补退款；
- 多人候补但座位不足；
- 候补目标舱位与释放座位舱位不一致；
- `SUCCESS` 候补写回正式 `ticket_order_id`，并在 `waitlist_passenger` 中记录座位。

AI 助手：

- “明天广州到上海的便宜航班”；
- “帮我找早上出发的航班”；
- “预算 800 以内”；
- “可以退改签吗”。

AI 推荐记录中的航班 ID 来自 seed 中实际存在的航班。

## 9. 已知限制

- 当前数据库没有独立航线表，航线数据以航班出发/到达机场组合体现。
- seed 使用可解释规则生成航班时刻，不追求真实航班时刻表。
- `dev`、`test`、`perf` 的 `seed-*.sql` 均为生成产物，默认被 `.gitignore` 忽略。
- 如果演示日期已过，请使用新的 `--base-date` 重新生成并导入。
- 大规模测试数据不要放入 Flyway migration，也不要导入生产数据库。

## 10. 后续扩展

- 可把更多真实机场、航司维护在脚本内的基础数据列表，或拆成本地 CSV/JSON；
- 如未来新增 `route` 表，可由当前 route pair 生成逻辑直接落表；
- 可为 perf profile 增加分片输出，降低单个 SQL 文件体积；
- 可在后端增加管理员校验接口，复用本指南中的一致性 SQL。
