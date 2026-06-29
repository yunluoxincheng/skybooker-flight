# 后端 Issue 清单 — 前后端契约审查（2026-06-29）

> 来源：[2026-06-29-frontend-backend-contract-review.md](2026-06-29-frontend-backend-contract-review.md)
> 范围：审查中发现的**需后端修复**的问题。前端侧问题见 [2026-06-29-issues-frontend.md](2026-06-29-issues-frontend.md)。
> 优先级：P0=阻断核心流程 / P1=明显坏体验 / P2=契约增强 / P3=低优先级

---

## B-CORS-1 ｜ 后端未配置 CORS，前端全部接口跨域失败

- **优先级**：P0（阻断，CRITICAL）
- **现象**：浏览器端登录（管理员/示例账户）及所有 API 调用均报 `Failed to fetch`；前端 dev（`localhost:3000`）无法与后端（`localhost:8080`）通信，整个前端等于静态壳。
- **根因 / 证据**：
  - `backend/src/main/java/com/skybooker/config/SecurityConfig.java` 全文无 `.cors(...)`，项目无 `CorsConfigurationSource` Bean、无 `WebMvcConfigurer.addCorsMappings`、无 `@CrossOrigin`。
  - 实测 `OPTIONS /api/auth/login`（`Origin: http://localhost:3000`）→ `403 "Invalid CORS request"`，且响应无 `Access-Control-Allow-Origin`。
  - 直连 `POST /api/auth/login`（不带 Origin）→ `401 {"code":10007}`，证明后端业务正常，纯跨域拦截。
- **建议修复**（安全配置，属红线变更，落地前需评审）：
  1. 在 `SecurityConfig.filterChain` 链上加 `.cors(CorsConfigurer -> {}）`（或 `Customizer.withDefaults()`）。
  2. 新增 `CorsConfigurationSource` Bean，**允许的 Origin 用环境变量配置**（不要硬编码、不要用 `*`，因前端发 `Authorization` 头需 `allowCredentials=true`，与 `*` 冲突）：
     ```java
     @Bean
     public CorsConfigurationSource corsConfigurationSource() {
         CorsConfiguration cfg = new CorsConfiguration();
         cfg.setAllowedOrigins(List.of(allowedOrigins.split(","))); // from @Value("${app.cors.allowed-origins}")
         cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
         cfg.setAllowedHeaders(List.of("Authorization","Content-Type"));
         cfg.setAllowCredentials(true);
         cfg.setMaxAge(3600L);
         UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
         src.registerCorsConfiguration("/**", cfg);
         return src;
     }
     ```
  3. 配置项 `app.cors.allowed-origins`：开发默认 `http://localhost:3000`；生产 `https://skybooker.yunluostar.com`（**已与用户确认的最终域名**）。
  4. 同步 `docs/11_DEPLOYMENT_GUIDE.md`：记录 `APP_CORS_ALLOWED_ORIGINS` 环境变量及默认值。
- **验证**：`curl -i -X OPTIONS http://localhost:8080/api/auth/login -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: POST"` 应返回 `2xx` 且带 `Access-Control-Allow-Origin: http://localhost:3000`；浏览器端实际登录打通。
- **影响面**：修复后前端所有接口才可用；是其余前后端问题得以验证的前置条件。

---

## B-ORDER-1 ｜ `OrderVO` 缺航班信息字段，订单详情/列表航班区域空白

- **优先级**：P0
- **现象**：用户在"我的订单"详情/列表看不到航司名、出发/到达城市、起降时间（只剩航班号），无法辨认"我买的哪趟航班"。
- **根因 / 证据**：
  - 后端 `order/vo/OrderVO.java` 字段仅 `flightNo`，**无** `airlineName / departureCity / arrivalCity / departureTime / arrivalTime`。
  - 前端 `types/order.ts:42-46` 声明并大量使用这些字段：`app/orders/[id]/page.tsx:235,239,243-248`、`app/orders/page.tsx:171-184`。
  - MyBatis 不会映射 VO 未声明的字段 → 前端取值恒 `undefined`。
- **建议修复**（二选一，推荐方案 A）：
  - **方案 A（后端补字段）**：`OrderVO` 增加 `airlineName / departureCity / arrivalCity / departureTime / arrivalTime`，`OrderMapper` 在订单详情与列表查询中 `JOIN flight + airport` 填充。注意列表查询性能（避免 N+1，用 JOIN 聚合）。
  - **方案 B（前端单独查）**：后端不改，前端用 `flightId` 调 `getFlightById` 补全——但订单列表多一次/条的请求，不推荐。
- **验证**：下单后查 `/api/orders/{id}` 与 `/api/orders`，响应含上述 5 个字段且非空。
- **文档影响**：若改 `OrderVO`，同步 `docs/07_API_DESIGN.md` 订单接口响应字段。

---

## B-FLIGHT-1 ｜ `FlightVO` 缺航司/机场内部 ID，admin 编辑航班无法回填

- **优先级**：P0（admin 功能不可用）
- **现象**：管理端"编辑航班"对话框打开时，航司 ID、出发/到达机场 ID 三个输入框为空；zod 校验 `coerce.number().min(1)` 阻止提交 → 编辑航班实际无法完成（新增正常）。
- **根因 / 证据**：
  - 后端 `flight/vo/FlightVO.java` 只返回 `airlineCode / departureAirportCode / arrivalAirportCode`（编码），**不返回** `airlineId / departureAirportId / arrivalAirportId`（数字 ID）。
  - 管理端 `updateFlight` 的 DTO `admin/dto/FlightFormDTO.java` 要求 `airlineId / departureAirportId / arrivalAirportId`（`@NotNull`）。
  - 前端 `app/admin/(app)/flights/page.tsx:100-113` 编辑回填时拿不到数字 ID，代码注释自述 *"FlightVO 不包含 airlineId/airportId，编辑时需要手动填入"*；而界面只显示名称，用户无从得知数字 ID。
- **建议修复**（推荐方案 A）：
  - **方案 A（后端补字段）**：`FlightVO` 增加 `airlineId / departureAirportId / arrivalAirportId`（admin 场景需要，用户端多返回字段无害）。`FlightMapper` 详情/列表查询补这三个字段。
  - **方案 B**：后端 `updateFlight` 不强制重传这三个 ID（缺失则保留原值），前端编辑表单对这三项做只读展示——但这样无法更换航司/机场，限制较大。
  - **方案 C（体验最佳）**：admin 用下拉选择航司/机场，需要后端补"航司列表""机场列表"查询接口。
- **验证**：admin 打开编辑对话框三项自动回填正确值；改任意字段可提交成功。
- **文档影响**：同步 `docs/07_API_DESIGN.md` 管理端航班接口响应字段。

---

## 排期建议

| Issue | 优先级 | 工作量 | 备注 |
|---|---|---|---|
| B-CORS-1 | P0 | 小（~30 行 + 配置） | **必须最先修**，解锁前端所有验证 |
| B-ORDER-1 | P0 | 中（VO + Mapper JOIN） | 影响用户端核心体验 |
| B-FLIGHT-1 | P0 | 中（VO + Mapper） | 影响 admin 航班管理 |

> 三项均为 P0，建议合到一个 `bugfix/backend-contract-gaps` 分支，修完前后端联调一次端到端（登录 → 搜航班 → 下单 → 订单详情 → admin 编辑航班）。
