# Sparkle 3 开发指南

本文件适用于仓库根目录及其所有子目录。开始修改前先阅读本文件；涉及具体功能时，再阅读
`README.md`、`docs/task-scheduling.md` 和对应模块代码。代码和测试是最终事实来源，文档与代码
不一致时，应在完成需求的同时修正相关文档。

## 项目概览

Sparkle 3 是一个 Spring Boot 单体应用，同时提供 BTN 客户端 API、浏览器 UI、后台管理、
社区/会员集成，并内嵌构建后的 Vue 3 前端。仓库还包含一个可独立安装到 Flarum 2 的扩展。

| 部分 | 技术栈 | 主要位置 |
| --- | --- | --- |
| 后端 | Java 25、Spring Boot 4.1.1、Spring Security、Spring Session、MyBatis-Plus、Flyway | `src/main/java` |
| 前端 | Vue 3.5、TypeScript 5.9（strict）、Vue Router、Pinia、Arco Design Vue、ECharts、Vite 7 | `frontend/src` |
| 数据库 | PostgreSQL 18、TimescaleDB、`pg_trgm` | `src/main/resources/db/migration` |
| 后台任务 | Spring Scheduling、`@MonitoredScheduledTask`、可热更新 Cron 模块 | `service/tasks`、`service/AnalyseModuleScheduler.java` |
| Flarum 集成 | Flarum 2、FoF OAuth、PHP 8.3、TypeScript/webpack | `flarum/extensions/sparkle-btn` |
| 交付 | Gradle 将 Vite 产物打入 Spring Boot jar，Dockerfile 仅复制已构建 jar | `build.gradle.kts`、`Dockerfile` |

核心请求面：

- `/ping/**`：BTN 客户端协议。客户端凭据由 `BasePingController` 校验，部分功能受 PoW 控制。
- `/api/ui/**`：浏览器用户 API。默认要求登录，少数公开接口在 `SecurityConfig` 中显式放行。
- `/api/admin/**`：管理员和协管 API。Spring Security 先校验 authority，随后
  `AdminAccessInterceptor` 每次请求重新读取数据库角色、封禁状态和协管模块权限。
- `/api/integrations/flarum/**`：供 Flarum 扩展调用，使用 `X-Sparkle-Token` 和服务端常量时间比较。
- `/api/integrations/memberships/**`：会员平台 webhook，使用平台级 webhook token 校验。
- `/debug/**`：仅管理员可用，不应作为稳定对外 API。

## 目录导览

| 路径 | 职责 |
| --- | --- |
| `src/main/java/com/ghostchu/btn/sparkle/controller` | MVC 端点、请求/响应 DTO、异常处理；按 `admin`、`ui`、`ping`、`integration`、`debug` 分组 |
| `src/main/java/com/ghostchu/btn/sparkle/service` | 业务逻辑、外部系统集成、配置和后台任务；实现类通常位于 `service/impl` |
| `src/main/java/com/ghostchu/btn/sparkle/mapper` | MyBatis-Plus mapper 和复杂查询接口 |
| `src/main/resources/mapper` | 对应 mapper XML |
| `src/main/java/com/ghostchu/btn/sparkle/entity` | 与数据库表映射的实体 |
| `src/main/java/com/ghostchu/btn/sparkle/autoconfig` | Security 之外的框架配置、Flyway 接管、Redis、MyBatis、静态资源 |
| `src/main/java/com/ghostchu/btn/sparkle/security` | OAuth 登录、principal、管理员策略和访问拦截 |
| `frontend/src/api` | 统一 HTTP 封装、API 方法和 TypeScript DTO |
| `frontend/src/views` | 页面；`views/admin` 为后台页面 |
| `frontend/src/components`、`layouts`、`composables` | 共享 UI、布局和组合式逻辑 |
| `frontend/src/i18n/locales` | 简体中文和英文本地化 |
| `src/test/java` | JUnit 5 测试；路径通常镜像生产代码路径 |
| `src/main/resources/db_sql` | 历史版本和兼容升级参考脚本，不由 Flyway 自动执行 |
| `flarum/extensions/sparkle-btn` | Flarum 扩展的服务端、前端、迁移和已提交构建产物 |

`build/`、`frontend/node_modules/`、`frontend/dist/`、`frontend/.vite/` 和 `.gradle/` 都是本地产物，
不要将其用于实现或手改。

## 工具链

- Java：25，由 Gradle toolchain 指定。
- Gradle：使用仓库 wrapper `9.7.1`，不要依赖系统 Gradle。
- Node.js：CI 使用 24。
- pnpm：`12.9.1`；主前端和 Flarum 扩展都使用 pnpm，不提交 npm/yarn lockfile。
- PHP（仅 Flarum 扩展）：8.3，启用 JSON 和 mbstring。
- 数据库：服务器使用 TimescaleDB（基于 PostgreSQL 18，PG18）；不要用 H2 验证 PostgreSQL/Timescale SQL。

如果本机默认 JDK 不是 25，优先安装 JDK 25 或让 Gradle toolchain 找到它。CI 的权威构建配置见
`.github/workflows/build-gradle.yml`。

## 常用命令

Windows PowerShell：

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat clean build --no-daemon
pnpm --dir frontend typecheck
pnpm --dir frontend build
pnpm --dir frontend dev
```

Linux/macOS：

```bash
./gradlew test --no-daemon
./gradlew clean build --no-daemon
pnpm --dir frontend typecheck
pnpm --dir frontend build
pnpm --dir frontend dev
```

说明：

- Gradle `processResources` 依赖前端构建，因此 `test`、`build` 等常见任务可能触发 pnpm
  `install --frozen-lockfile` 和 Vite 构建。
- Vite 开发服务器默认监听 `5173`，把 `/api`、OAuth、头像和 `/ping` 请求代理到
  `http://localhost:7799`。
- `Dockerfile` 不会构建源码，只复制 `build/libs/sparkle-0.0.1-SNAPSHOT.jar`。部署镜像前先完成
  Gradle 构建。
- 主前端当前没有单元测试或 lint script，验证入口是 TypeScript check、生产构建和浏览器手测。

只运行部分 Java 测试时：

```powershell
.\gradlew.bat test --tests "com.ghostchu.btn.sparkle.service.DatabaseConfigServiceTest" --no-daemon
```

### Flyway 集成测试

`FlywayMigrationIntegrationTest` 会建库并执行破坏性操作，默认在没有环境变量时跳过。仅在专用测试库
中运行，数据库名必须包含 `test`：

```powershell
$env:SPARKLE_FLYWAY_TEST_URL = "jdbc:postgresql://localhost:5432/sparkle_test"
$env:SPARKLE_FLYWAY_TEST_USER = "postgres"
$env:SPARKLE_FLYWAY_TEST_PASSWORD = "..." # 不要把真实密码写入仓库
$env:SPARKLE_FLYWAY_TEST_ALLOW_DESTRUCTIVE = "true"
.\gradlew.bat test --tests "com.ghostchu.btn.sparkle.autoconfig.FlywayMigrationIntegrationTest" --no-daemon
```

普通测试通过但该测试被跳过，不等于迁移已在真实 PostgreSQL/TimescaleDB 上验证。

### Flarum 扩展

```powershell
pnpm --dir flarum/extensions/sparkle-btn/js typecheck
pnpm --dir flarum/extensions/sparkle-btn/js build
```

扩展前端源码位于 `js/src`，`js/dist/forum.js` 和 `js/dist/admin.js` 是仓库跟踪的生产产物。修改
`js/src`、样式或 webpack 输入后必须重建并一起检查 dist 差异。不要在 Flarum 站点目录中直接修改
本仓库里的扩展源码。

## 修改原则

- 保留用户已有未提交改动，不回退、不覆盖、不顺手重排无关代码。
- 避免整文件格式化或批量换行符转换；先看 `git diff`，只提交与任务相关的行。
- 先找现有同类实现并沿用其分层、DTO、错误码、命名和测试方式，再考虑抽新抽象。
- 修 bug 时先补能复现问题的测试；只有行为约定确实变更时才修改已有断言，不通过删除测试“修复”构建。
- 新增用户可见功能时同步处理 API、路由、鉴权、国际化、响应式布局和空/错/加载状态。
- 修改公共协议、配置键、数据库结构或运维流程时同步更新文档。
- 不提交密钥、token、密码、真实数据库 URL、用户个人数据或生产日志。

## 用户可见文案与信息暴露

- 不要向最终用户暴露实现细节，包括堆栈信息、SQL、表名、类名、内部任务 ID、配置键、Redis key、
  环境变量、服务器路径、第三方原始响应、完整 IP/User-Agent 或任何密钥。管理员排障界面可以提供必要
  技术信息，但仍须遵守隐私和敏感信息规则。
- 普通用户错误文案只说明发生了什么、是否影响操作、下一步可以做什么；需要追踪时可以展示仅供客服
  使用的错误追踪 ID，而不是异常原文。不要把内部异常消息直接透传到浏览器。
- 文案应像自然的产品界面，简洁、具体、克制。优先使用“操作结果 + 影响 + 下一步”，例如
  “删除后无法恢复。请确认是否继续”，不要使用空泛承诺、夸张形容词或营销口号。
- 不要生成过于 AI 化的文案：避免机械的三段式说教、重复总结、无意义的“值得注意的是/总而言之”、
  大量感叹号、堆砌 emoji、强行共情、空泛的“赋能/无缝/智能体验”以及为了正式而过度冗长的表达。
- 中英文文案保持相同含义和语气，使用用户熟悉的术语；不要逐字硬翻译中文，也不要在面向用户的句子中
  混入开发约定、类名或函数名。
- 通知、邮件、空状态、确认框、错误提示和公告都属于最终用户文案，适用相同规则；后台技术页面可以更
  详细，但也不能暴露密钥、密码和个人隐私数据。

## 提交与拆分

- 每次完成代码、测试、配置或文档修改并通过相关验证后，必须主动执行 git commit，无需等待用户另行要求。
  除非用户明确要求不提交，否则提交完成后才能交付；只提交本次任务的改动，保留用户已有未提交改动。
- 如果 GPG 签名授权等待超时且用户未响应，必须使用 git commit --no-gpg-sign 重试提交，不要因此搁置交付。
- 每个模块的修改必须单独提交；不要把多个模块或互不相关的功能混在同一个 commit 中。
- 前端和后端必须分别提交，即使它们属于同一个功能。后端 commit 只包含后端代码、迁移和后端测试；
  主前端 commit 只包含 `frontend/` 下的页面、API 类型、样式和国际化改动。
- 前后端联调功能推荐先提交后端，再提交前端。分别验证后端的测试/编译和前端的
  `typecheck`/`build`，不要等全部改完再用一个混合 commit 提交。
- Flarum 扩展同样按模块拆分：PHP 服务端、迁移及 Flarum 服务端配置单独提交；`js/src` 和重建后的
  `js/dist` 作为 Flarum 前端单独提交。
- 纯文档更新（包括 `AGENTS.md`）单独提交；只有文档必须是某模块实现的一部分时才随该模块提交。
- 提交时显式 stage 目标模块的路径，禁止使用 `git add .` 或 `git add -A` 一次性暂存所有工作区改动。
- 每个 commit 应只包含一个可说明、可验证的变更范围。多模块功能应在最终交付说明中列出各模块对应的
  commit，并保持清晰的提交顺序。

## 后端约定

### 分层与 DTO

- Controller 负责映射、参数边界和身份解析；跨表业务、事务和外部调用放在 service。
- Java 保持 4 空格缩进；不要用全仓格式化工具制造无关差异。
- 每个功能优先将 DTO 放进同域 `dto/*Models.java` 的嵌套 record 中，不要把 `entity` 直接暴露为
  新的浏览器契约。
- 成功响应通常直接返回 DTO、列表或分页对象；异常由 `GlobalExceptionHandler` 统一转换为
  `StdResp<T>`，即 `{ success, message, data }`。前端 `request<T>` 对应前者，错误读取 `message`。
- 可预期的 4xx 使用 `BusinessException(HttpStatus, message)`，也允许现有代码中的
  `ResponseStatusException`；保留有意义的 HTTP 状态，不要全部降级成 200。
- 分页结果沿用 `records/current/size/total/pages`。限制过大的 `size`，现有接口通常上限为 100，
  MyBatis 通用分页上限为 1000。
- Java ID 与时间继续使用现有类型：数值主键用 `Long/long`，时间用 `OffsetDateTime`，IP 用
  `InetAddress`，UUID 用 `UUID`，金额用 `BigDecimal`。
- JSON 字段默认 camelCase。对旧客户端或外部协议需要 snake_case 时，在 DTO 上显式使用
  `@JsonProperty`，不要修改整个应用的命名策略。
- 浏览器不能安全表示的 64 位积分、流量和部分用户 ID 在已有契约中以十进制字符串传输。保持该约定，
  不要为了“方便”改成 JavaScript `Number`。

### 鉴权与身份

- Controller 中的当前用户优先通过 `@AuthenticationPrincipal SparkleUserDetails` 获取。需要最新封禁
  或角色状态时，再按 UID 查询数据库。
- `/api/**` 默认要求登录。新增公开端点必须在 `SecurityConfig` 中显式放行，并证明端点自身完成认证。
- 新增管理员端点默认只允许 admin；如果协管也应访问，必须同时更新：
  - `src/main/java/com/ghostchu/btn/sparkle/security/AdminPolicy.java`
    的 `canAccessModeratorModule`
  - `frontend/src/App.vue` 的 `moderatorRouteNames`
  - `frontend/src/layouts/AdminLayout.vue` 的 `moderatorKeys`
- 不要只靠前端隐藏菜单或路由来授权；服务端 `SecurityConfig`、拦截器和 service 层仍要做必要校验。
- 浏览器写请求使用 SPA CSRF cookie。统一通过 `frontend/src/api/http.ts` 发出请求，由它读取
  `XSRF-TOKEN` 并写入 `X-XSRF-TOKEN`。新增 `/ping/**`、webhook 等 CSRF 豁免必须有明确协议原因。
- 外部集成 token 使用常量时间比较。不得把 token 序列化到前端、写入普通响应或日志。

### 隐私、封禁与审计

- 公开资料、评论、排行榜、Flarum 卡片等输出必须遵守 `UserPrivacyService` 的头像、用户名和积分
  可见性设置，并遵守资料审核状态。
- 只有本人、明确授权的 Flarum 用户或管理员场景才能看到受保护资料；不要复制一份绕过隐私逻辑的
  “简化 DTO”。
- 用户应用密钥只在创建或重置时返回。不要新增读取明文密钥的端点，也不要记录请求头中的凭据。
- 新管理操作若改变用户、积分、会员交易、权限或删除状态，应保留审计信息或可追溯日志；
  不记录完整密钥和个人隐私正文。
- 删除账户、删除用户应用、封禁用户等操作有级联和业务前置条件。沿用现有 service 的特殊处理，
  不要在 Controller 中直接执行无边界的批量删除。

### 实体、Mapper 与事务

- 实体使用 MyBatis-Plus 注解和 Lombok `@Accessors(chain = true)`，表名/列名保留 snake_case，
  Java 字段使用 camelCase。表 `user` 是保留字，映射时必须写作 `"user"`。
- 简单 CRUD 使用 `BaseMapper`/`IService`；复杂 SQL 放进 `src/main/resources/mapper/*.xml`，
  mapper 接口参数使用 `@Param`。已有 JSONB、IP、UUID 类型处理器，不要重复实现。
- SQL 值使用 `#{}` 绑定。只有经过严格白名单或格式校验的内部标识符才可动态拼接；参考
  `AdminStorageMapper.SqlOptionValue` 和 `SqlRelation`。不要把请求参数用 `${}` 直接拼进 SQL。
- 多步写入、余额变更、唯一绑定、删除流程必须在 service 层使用 `@Transactional`，不能被捕获后
  吞掉导致部分成功。确有独立提交需求时显式使用 `REQUIRES_NEW`。
- 事务提交后再执行邮件、外部通知等副作用。沿用 `TransactionSynchronization`/事务事件或在提交后
  启动虚拟线程的方式，避免回滚后仍发送消息。
- 大表查询应有条件、分页或合理的 `LIMIT`；避免无边界 `selectList` 和全表更新。MyBatis-Plus
  `BlockAttackInnerInterceptor` 会阻止缺少条件的全表更新/删除。
- 幂等写优先依赖数据库唯一约束、条件更新或 `upsert`，不要只做“先查再插”的无锁判断。

### 并发与后台任务

- I/O 型异步操作可使用虚拟线程；需要生命周期管理的周期性任务使用 Spring Scheduler。
- 调度任务添加 `@MonitoredScheduledTask`。`id` 必须全局唯一且稳定，`group`、`scheduleType`、
  `expression`/`intervalMillis` 必须和真实调度一致，名称/描述使用 i18n key。
- 可后台热更新启停和 Cron 的任务通过 `AnalyseModuleConfigService` 和
  `AnalyseModuleExecutionService` 注册，并补充中英文任务翻译。完整接入步骤见
  `docs/task-scheduling.md`。
- Cron 为 Spring 六段格式：`秒 分 时 日 月 星期`，使用 JVM 时区。生产 Docker 默认 `TZ=UTC`。
- 事件任务和启动任务先 `ensure(TaskDefinition...)`，执行时只调用一次 `success`、`skipped` 或
  `failure`。最近执行记录只保存在内存，不是持久审计系统。
- 长任务应分批、可重试、控制数据库压力，并避免阻塞 HTTP 请求或调度线程。

### Redis 与外部 I/O

- 使用已有限定名：
  `stringStringRedisTemplate`、`stringLongRedisTemplate` 或 `stringObjectRedisTemplate`。
  不要临时创建未配置序列化器的 `RedisTemplate`。
- Redis key 统一使用 `sparkle:` 前缀；全站 key 尽量放进 `RedisKeyConstant`。浏览器 session 使用
  Redis indexed session，默认业务缓存 TTL 为 5 分钟。
- 外部 HTTP 调用设置连接/读取超时、有限响应大小和明确异常映射。Flarum 扩展已有超时、响应上限和
  UID 一致性校验，可作为参考。
- 外部服务失败必须保留可诊断日志，但不得阻断不相关的主流程；例如 Flarum 同步失败不应阻止登录。

## 数据库与 Flyway

服务器使用 TimescaleDB（基于 PostgreSQL 18，PG18）。设计存储、查询和迁移时，可以使用 TSDB 的
高级功能，包括超表（hypertable）、列存、压缩、连续聚合以及自动保留和刷新策略等。具体语法和能力
应以服务器安装的 TimescaleDB 扩展版本为准，并按下述迁移与集成测试要求验证。

应用启动自动执行 `classpath:db/migration`。全新库从 `V1__initial_schema.sql` 建库；已有生产库由
`FlywayMigrationConfig` 和 `LegacySchemaBaselineResolver` 识别版本后建立基线，再执行后续迁移。
不要用手工 SQL 代替新的增量迁移。

新增 schema 变更：

1. 备份或使用专用测试库，确认 TimescaleDB 可用。
2. 新建下一个最高版本的
   `src/main/resources/db/migration/V<next>__<snake_case_description>.sql`。
3. 若迁移包含 TimescaleDB 连续聚合或 policy 等不能放在单事务中的语句，按现有模式添加同名
   `.sql.conf`，内容为 `executeInTransaction=false`。
4. 同步更新实体、mapper/XML、service 和相关测试。
5. 在包含 `test` 的专用 PostgreSQL/TimescaleDB 数据库运行完整 Flyway 集成测试。
6. 更新 `README.md`，说明升级影响、维护窗口需求或兼容策略。

限制：

- 永远不要修改、重命名或删除已经发布的 migration；Flyway 会校验 checksum。
- `db_sql/` 是历史脚本/手工兼容参考，不是新的 schema 入口。新变更只追加到 `db/migration/`。
- 应用只应执行 `CREATE EXTENSION IF NOT EXISTS timescaledb` 一类已约定操作；安装扩展和授予 DDL
  权限是部署前置条件。
- 生产大表迁移应考虑锁、回填、索引创建和连续聚合刷新成本，说明是否需要维护窗口。
- 时间窗口统计使用明确的时区语义；持久化时间继续使用 `OffsetDateTime`，不要依赖服务器默认时区。

累计流量和生涯贡献由 CareerIngestionService 在业务写入事务内维护，不使用本功能的存储函数或触发器。
V61/V62 不回填历史；去重检查点不随原始明细清理。升级限制见 README。

## 业务配置与密钥

业务配置由 `app_config_entries` 管理。非数据源 YAML 默认值会在启动时迁移到数据库，已有数据库值
不会被覆盖。生效优先级为：

1. 命令行参数
2. 环境变量
3. 数据库配置
4. YAML 默认值

规则：

- 业务配置键使用 `sparkle.<domain>.<kebab-case-setting>`。
- `spring.datasource.*` 和 `spring.flyway.*` 不进入数据库配置，必须通过启动期环境提供。
- 密钥类键由名称自动识别为 sensitive，后台只显示“已配置”，不得回显原值。新增密码、token、
  secret、credential、authorization、API key 等配置时沿用该机制。
- 只有 `DatabaseConfigService.HOT_RELOAD_KEYS` 或允许的 `sparkle.*` 前缀会热更新。新增运行时可改
  配置键时，确认 `isHotReload`、服务缓存失效和相关调度同步更新；否则明确说明需要重启。
- 仓库中的 `application.yaml`、`application-local.yaml` 和 `application-production.yaml` 被
  `.gitignore` 忽略。不要在文档、测试或提交中放入真实凭据。
- 修改配置默认值时，检查 YAML 扫描、`V44` 等已有默认值播种方式和部署文档，避免同一键有三个互相
  冲突的默认值。

## 前端约定

- 使用 Vue 3 Composition API 和 `<script setup lang="ts">`。TypeScript 开启 strict；保持现有
  2 空格、单引号和 `@/` alias 风格。
- API 方法统一加到 `frontend/src/api/index.ts` 或 `admin.ts`，DTO/响应类型加到
  `api/types.ts` 或 `api/adminTypes.ts`，请求统一经过 `request<T>`。
- 不要绕过 `api/http.ts` 自行实现 fetch，否则会漏掉 CSRF、401 事件和统一错误解析。
- 所有用户可见文案通过 vue-i18n，至少同步：
  - `frontend/src/i18n/locales/zh-CN.ts`
  - `frontend/src/i18n/locales/en-US.ts`

  不要只补一种语言，也不要硬编码新的提示语。
- 新增页面在 `router/index.ts` 中懒加载。若需要浏览器直接访问新路径，同时加入
  `SpaController.java` 的映射列表；否则刷新页面可能返回 404。
- 新后台页面还要检查 `AdminLayout.vue` 导航、普通用户/协管可见性、`App.vue` 路由约束和后端
  `AdminPolicy`。
- 64 位字节数、积分和 ID 使用 `BigInt` 或 `useFormatters`，不要直接做会丢精度的 `Number` 运算。
- ECharts 图优先复用 `EChart.vue` 和 `utils/charts.ts`；日期/字节/百分比优先复用
  `useFormatters` 和 `errorMessage`。
- 样式优先使用 Arco CSS 变量和 `styles/main.css` 的 `page-stack`、`surface-card`、`table-card`、
  `filter-actions` 等共享类。新布局至少考虑 720px 和 980px 断点。
- `frontend/src/components.d.ts` 由 Vite 自动生成，`build/generated/frontend` 是构建产物；
  不要手改。
- 独立页通过 route meta 的 `standalone` 控制。`public` 只是一项语义标记，匿名访问仍需同时检查
  `SecurityConfig` 和 `SpaController`。认证失败会触发 `sparkle:unauthorized`，由 `App.vue`
  清理 session 并跳转登录页。

## Flarum 扩展约定

- 扩展只在服务端保存 Sparkle token；不得通过 Flarum API 把 token 返回给浏览器。
- `sparkleBtn` 字段的可见性必须在 `UserResourceFields` 服务端判断：本人或拥有
  `sparkleBtn.viewUserData` 权限的用户。
- 上游 API 响应中的字节值保持十进制字符串。同步时校验 GitHub UID、BTN UID 和响应结构，失败时保留
  上一次成功缓存。
- GitHub 登录、绑定、解绑、注册事件由 `extend.php` 注册；涉及用户权限组的变更必须同时考虑这些事件。
- 扩展迁移位于 `flarum/extensions/sparkle-btn/migrations`。已发布迁移不要修改，新增时间和文件名
  遵循现有顺序。
- PHP 代码使用 PSR-4：`Sparkle\Flarum\` 映射到 `src/`。新增依赖先更新 `composer.json`，不要手工
  改第三方包。
- 主前端与 Flarum 前端是两套独立构建；修改 Flarum `js/src` 后只运行根前端构建不会更新扩展产物。

## 常见改动清单

### 新增用户页面/API

1. 后端 DTO、Controller、service、mapper。
2. 判断是否需要更新 `SecurityConfig`。
3. 前端 API 方法和类型。
4. 页面、router 和必要时 `SpaController`。
5. 中英文文案、移动端布局、加载/空/错误状态。
6. Controller/service 测试；跨层功能运行前端 typecheck/build。

### 新增后台功能

1. `/api/admin/**` Controller 和 DTO。
2. 明确 admin-only 还是 moderator-accessible。
3. 若允许协管，同步 `AdminPolicy`、`App.vue`、`AdminLayout.vue`。
4. 后台页面、导航、路由和中英文文案。
5. 权限拒绝、输入边界和审计测试。

### 新增数据库变更

1. 追加 Flyway migration，不修改历史文件。
2. 更新 entity/mapper/XML 和查询测试。
3. 需要时添加 `.sql.conf`。
4. 在 TimescaleDB 专用测试库验证迁移。
5. 更新升级说明。

### 新增后台任务

1. 稳定任务 ID、分组、调度类型和 i18n key。
2. 普通 Spring 任务配对 `@MonitoredScheduledTask`；需要热更新时接入分析模块体系。
3. 补中英文任务名称/描述。
4. 验证手动执行、失败/跳过状态、重叠执行和关闭模块后的行为。
5. 阅读并遵循 `docs/task-scheduling.md`。

### 新增运行配置

1. 使用 `sparkle.*` 键并确定默认来源。
2. 敏感键不进入普通 API 明文。
3. 确认是否需要热更新；需要时更新 `HOT_RELOAD_KEYS`/前缀并让 service 缓存失效。
4. 更新后台表单、配置说明和测试。

## 验证与交付

至少执行与改动相关的检查；跨层或高风险改动执行完整构建。

| 改动类型 | 最低验证 |
| --- | --- |
| 后端局部逻辑 | 对应 `--tests`，必要时相关 mapper/service 测试 |
| API/安全 | Controller、SecurityConfig、AdminPolicy 测试，检查 401/403/409/429 |
| 主前端 | `pnpm --dir frontend typecheck`，再运行 `pnpm --dir frontend build` |
| 前后端契约 | Java 测试 + 前端 typecheck/build，手测至少一次真实请求 |
| Flyway/TimescaleDB | 专用测试库上的完整 `FlywayMigrationIntegrationTest` |
| Flarum 服务端 | `php -l` 相关文件；检查事件、权限和迁移路径 |
| Flarum 前端 | `pnpm --dir flarum/extensions/sparkle-btn/js typecheck` 和 `build` |
| 跨模块/准备交付 | 可运行环境下 `.\gradlew.bat clean build --no-daemon` |

交付前检查：

- `git diff` 只包含任务相关改动，用户原有改动仍保留。
- 没有手改生成目录，没有提交凭据、真实配置或调试临时文件。
- 新行为已有测试或明确说明无法自动测试的原因。
- 新用户可见内容已中英双语。
- 用户可见内容没有泄露内部实现、异常详情、配置或敏感数据，也没有明显模板化、夸张或过度 AI 化的表达。
- 新路由在刷新、未登录、权限不足和移动端场景下行为正确。
- API 和 schema 变更已同步文档。
- 最终说明实际运行过的命令；未运行 Flyway/浏览器/Flarum 真实环境验证时应明确写出。
- 提交前用 `git show --stat` 或 `git diff --cached --stat` 确认每个 commit 只涉及一个模块；前后端
  改动必须出现在不同 commit 中。
- 提交必须沿用现有 Conventional Commit 风格，例如 `feat: ...`、`fix: ...`、
  `refactor: ...`，并可用模块 scope 区分，例如 `feat(backend): ...`、
  `feat(frontend): ...`。

当项目架构、构建流程、权限模型或数据库约定发生变化时，同步更新本文件。
生涯统计按 UTC 日汇总，新记录从升级后开始累计；历史不回填。
