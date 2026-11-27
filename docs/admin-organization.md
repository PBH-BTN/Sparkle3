# 后台管理分类与迁移清单

本次调整仅覆盖 Sparkle 后台管理页面、管理 API、权限约束和验证工具。用户前台页面、用户导航、个人设置及 Flarum 扩展后台保持原有实现。

## 导航与页面注册

后台采用顶部大类、左侧分项、右侧内容。大类为：首页、全局、界面、用户、内容、BTN、运营、应用、工具、站长。

所有 BTN 配置分组、客户端特征收集、查询评论开关、积分规则、用户应用、版本提醒、用户流量统计和任务中心统一归入「BTN」。原 BTN 配置不再分散到全局、用户或站长；相关参数、开关和执行计划均从 BTN 进入。

唯一前端注册表为 `frontend/src/router/adminNavigation.ts`：

- `adminCategories` 定义顶部大类。
- `adminPages` 定义分项、路径、路由名称、懒加载页面、页面参数和协管可见性。
- `adminLegacyRedirects` 保留旧入口的兼容跳转。
- `adminRoutes.ts` 由注册表生成路由；`App.vue` 和 `AdminLayout.vue` 复用同一权限清单。
- 后端 `SpaController` 显式注册浏览器地址，`AdminPolicy` 和 `AdminAccessInterceptor` 独立校验最新角色。

页面复用组件时由路由传入 `group`、`scope` 或 `section`。页面切换按路径重新建立实例，避免沿用另一表单的草稿。页签和任务过滤使用查询参数；旧 GeoIP 地址跳转到 `/admin/rules?tab=geoip`。

搜索覆盖当前角色可以访问的全部分项。各大类记住上次访问的分项，失效或无权限的记录回退到该类第一个可用分项。980px 及以下使用抽屉导航，720px 及以下搜索独占一行。

## 旧入口对照（26 项）

| 旧路径（均以 `/admin/` 开头） | 新归属与迁移内容 |
| --- | --- |
| `dashboard` | 首页 → 后台概况，地址不变 |
| `tasks` | BTN → 任务中心，地址不变 |
| `ban-submissions` | BTN → 重复封禁记录，地址不变 |
| `homepage-statistics` | 工具 → 首页统计更新，地址不变 |
| `announcements` | 运营 → 公告管理，地址不变 |
| `messages` | 运营 → 站内信发送，地址不变 |
| `community-groups` | 用户 → 社区群组；凭据独立到应用 → 社区平台接入 `community-integration` |
| `memberships` | 兼容跳转到 `membership-transactions`；拆为交易账本、许可证、同步与对账、支持与结算规则、会员平台接入 |
| `navigation` | 界面 → 导航设置，地址不变 |
| `settings` | 兼容跳转到 `traffic-statistics`；原内容拆分见下表 |
| `version-alerts` | BTN → 版本提醒，地址不变 |
| `users` | 用户 → 用户管理，地址不变 |
| `points-modules` | BTN → 积分规则；计划和任务启停跳转任务中心 |
| `userapps` | BTN → 用户应用，地址不变 |
| `github-sync` | BTN → GitHub 规则同步，地址不变 |
| `flarum-integration` | 应用 → Flarum 集成，地址不变 |
| `content-moderation` | 内容 → 词语过滤，地址不变 |
| `ai-moderation` | 内容 → AI 审核设置；评论策略到 BTN → `comment-policy`，审核队列和重试到 `moderation-queue` |
| `ip-query-comments` | 内容 → 评论管理，地址不变 |
| `btn` | 兼容跳转到 `btn/communication`；全部字段分配到下述 15 个表单 |
| `modules` | BTN → 自动分析；业务参数留在本页，计划和任务启停跳转任务中心 |
| `rules` | BTN → 规则管理，保留手动规则和管理员专属 GeoIP 页签 |
| `exports` | 全局 → 导出设置，地址不变 |
| `database-config` | 站长 → 高级配置，已知配置优先跳转业务编辑器 |
| `user-contact-emails` | 站长 → 用户邮件审计，地址不变 |
| `storage` | 站长 → 数据与存储，数据库维护；生涯去重保留期在 BTN → 上报数据保留 |

额外兼容入口：`/admin/afdian` → `/admin/membership-providers`；`/admin/geoip-city-blacklist` → `/admin/rules?tab=geoip`。

## 原运行设置

| 内容 | 唯一常规编辑位置 |
| --- | --- |
| 客户端特征收集 | BTN → 客户端特征收集 `/admin/site-features` |
| 用户流量统计、窗口、刷新间隔与运行状态 | BTN → 用户流量统计 `/admin/traffic-statistics` |
| 首页流量显示与各指标可见范围 | 界面 → 首页展示 `/admin/homepage-display` |
| 页脚支持按钮、链接、声明、备案、时间和性能指标 | 界面 → 页脚设置 `/admin/footer` |
| SMTP、POP3、测试和收信状态 | 站长 → 邮件服务器 `/admin/mail` |
| 用户联系邮件标题与正文模板 | 站长 → 用户邮件模板 `/admin/contact-template` |
| 生涯去重保留期 | BTN → 上报数据保留 `/admin/btn/retention`（管理员专属表单） |

原表单中一并加载和提交、但没有实际渲染的协议版本和查询 PoW 字段由对应 BTN 表单管理，不再随其他设置重提。

## BTN 字段完整性

`AdminBtnConfigGroups` 为 `BtnConfigCatalog` 的每个字段指定一个编辑器。配置键和运行时读取方式不变。

| 表单 `/admin/btn/<group>` | 分类与内容 |
| --- | --- |
| `communication` | BTN：协议、重新配置与服务路由 |
| `reporting` | BTN：封禁、种群和心跳上报 |
| `rule-delivery` | BTN：规则下发接口与拉取参数 |
| `reporting-risk` | BTN：重复封禁、重传停用、上报量阈值与停用邮件模板 |
| `query` | BTN：IP 查询、评论计数、限流与查询窗口 |
| `ranking` | BTN：排行榜计算权重 |
| `security` | BTN：PoW 与上传安全 |
| `points-timezone` | BTN：积分时区 |
| `auto-accounts` | BTN：自动账户 |
| `ip-warning` | BTN：用户 IP 告警 |
| `measure` | BTN：网络测量 |
| `mail-identities` | BTN：系统与用户邮件发件身份 |
| `retention` | BTN：种群与心跳保留、清理计划 |
| `cache` | BTN：头像缓存目录、有效期与清理计划 |
| `maintenance` | BTN：封禁模块名称缓存维护计划 |

业务参数不包含重复的分析任务 Cron 或任务开关。BTN → 任务中心仍支持原有可编辑的模块 Cron、启停和手动执行，固定间隔、事件、启动任务的限制保持不变。Peer 分布的两个模块仍由任务中心独立管理。

首页统计部分开关同时控制刷新与展示，沿用现有行为；本次没有新增互相独立的展示开关或更改统计口径。

## 独立保存与权限

- 新增管理员专属 `GET/PUT /api/admin/settings/{features|statistics|homepage-display|contact-template}`，每个接口仅写入自己拥有的配置键。
- 新增 `GET/PUT /api/admin/btn/groups/{group}`；提交其他分组的字段或空提交返回 400，未知分组返回 404。
- `GET /api/admin/btn/editors` 返回配置键与编辑位置，用于高级配置跳转。
- 原 `/api/admin/config` 和 `/api/admin/btn` 保持兼容，新的后台表单不再使用它们整组提交。
- 邮件通道表单不再提交发件身份。邮件 API 省略身份时保留当前身份，密码留空继续沿用既有密码。
- 每个表单独立处理加载、错误和保存状态。新拆分的设置表单在离开或重新加载前提示未保存修改；会员页面按子表单记录草稿，保存一项不会把其他未保存修改标记为已保存。
- 移动菜单不改变授权。原 BTN 配置字段继续允许协管；原管理员专属功能保持专属。GeoIP 页签不向协管显示，也不加载其接口。
- 任务页与分析业务页互相链接，并支持按模块定位。高级配置中有业务编辑器的项目跳转业务页面，其他普通键继续使用原始编辑器；密钥继续只写不回显。

## 验证入口

常规检查：

```powershell
pnpm --dir frontend typecheck
pnpm --dir frontend build
.\gradlew.bat test --no-daemon
```

`check-admin-migration.mjs` 纳入 typecheck/build，检查页面唯一性、分类、懒加载、文件存在性、SPA 注册、28 个原有地址、15 个 BTN 编辑器、拆分页面参数、中英文标签，以及原业务操作和表单控件是否保留。后端 `AdminRouteRegistrationTest` 对照同一清单测试管理员与协管的浏览器入口。

真实浏览器联调使用测试类 `com.ghostchu.btn.admincheck.AdminBrowserTestApplication`。该类不进入生产 jar，只接受 localhost 且库名含 test 的专用数据库；使用独立 Redis（127.0.0.1:16388），HTTP 仅监听 127.0.0.1:17799。

```powershell
# 先准备独立 PostgreSQL 18 / TimescaleDB 测试库和 Redis，密码只通过环境变量提供。
$env:SPARKLE_ADMIN_TEST_URL = 'jdbc:postgresql://127.0.0.1:15438/sparkle_admin_test'
$env:SPARKLE_ADMIN_TEST_PASSWORD = '<temporary-test-password>'
.\gradlew.bat runAdminBrowserTest --no-daemon
# 等日志出现 ADMIN_BROWSER_TEST_READY 后，在另一个终端运行：
pnpm --dir frontend exec playwright install chromium
pnpm --dir frontend test:admin
```

测试程序保留真实 Controller、服务、权限过滤器、CSRF、PostgreSQL 和 Redis。仅测试入口提供本地测试身份；GeoIP 下载与本机 YAML 配置导入被排除，防止导入真实凭据或触发下载。浏览器拒绝外站请求；第三方授权、远程订单同步和真实邮件投递需另行使用授权的外部环境验证。

浏览器结果与截图位于 `build/reports/admin-browser/`。表单测试只允许本地测试入口；`SPARKLE_ADMIN_FORM_FILTER` 可按名称选择复测项（涉及用户和应用的测试有创建依赖，完整回归应不设置该变量）。

存储策略另有真实 TimescaleDB 回归 `AdminStoragePolicyIntegrationTest`，沿用 `SPARKLE_FLYWAY_TEST_*` 环境变量和专用测试库保护。测试验证保留策略的添加、删除、重复删除，以及没有压缩策略时的移除。数据库函数的 void 返回值按文本接收，不再作为 boolean 读取；调用禁用查询缓存并标记写入，兼容生产环境的 MyBatis 拦截器。

后台评论回归覆盖匿名作者列表和没有编辑者的历史版本，避免空用户映射导致空指针异常。

## 本次构建验证（2026-11-28）

- `pnpm --dir frontend typecheck`、`pnpm --dir frontend build` 通过；迁移清单检查已纳入这两个命令。
- `./gradlew.bat clean build --no-daemon` 在独立 LF 检出副本通过，使用 Java 25、PostgreSQL 18.6 和 TimescaleDB；405 个 Java 测试，402 个通过、3 个按条件跳过。
- 全量 SQL 测试使用单独的空测试库，浏览器联调使用已迁移的另一测试库。不要把需要创建测试表的 SQL 用例指向已导入业务 schema 的联调库。
- Windows CRLF 检出会让既有 V88 邮件模板与 Java 文本块的换行断言不一致。本次在 LF 副本执行完整验证，未修改历史迁移和既有断言。
- `pnpm --dir frontend test:admin` 通过：285 项页面、导航、权限、中英文、深浅主题和响应式检查；28 项真实表单/API 操作检查。包含存储压缩与保留策略的启用、停用与返回状态，以及保存后回读、部分保存后的未保存提示。
- Java HTML 报告保存在 `build/reports/admin-full-build/index.html`；浏览器结果和截图保存在 `build/reports/admin-browser/`。

真实 SMTP/POP3 连接、第三方 AI 调用、第三方授权和远程订单同步未使用生产凭据验证。配置保存、敏感值不回显、入口和本地业务请求在隔离环境验证；汇率表单使用本地 HTTP 汇率源验证保存和重算。

## 提交拆分

| 模块 | 提交 | 内容 |
| --- | --- | --- |
| 后端 | `0754e3e` | 存储策略 void 返回值修复 |
| 后端 | `e245a57` | 后台匿名评论读取修复 |
| 后端 | `88708d2` | 运行设置独立保存接口 |
| 后端 | `1925755` | BTN 分组配置接口 |
| 后端 | `1657459` | 邮件保存保留未提交的发件身份 |
| 后端 | `f389ad0` | SPA 路由注册与本地真实联调入口 |
| 后端 | `7443d7e` | 存储策略与生产 MyBatis 拦截器兼容 |
| 前端 | `3667083` | 分类导航、设置与页面拆分、关联入口和移动布局 |
| 前端验证 | `dc0410b` | 完整性清单、浏览器和真实 API 回归工具 |

## BTN 归属约束补充

BTN 分类包含 26 个分项，原有 52 个页面、28 个旧地址和权限保持兼容。`check-admin-migration.mjs` 额外校验全部 15 个 BTN 配置编辑器及相关业务入口必须属于 BTN，防止后续再次分散。侧栏较长时会自动滚动到当前分项。

客户端特征收集会处理封禁与种群上报，因此从原站点功能入口归入 BTN。生涯去重保留表单移入 BTN → 上报数据保留，继续限制为管理员可见；协管访问该分组时只加载原有授权字段。高级配置对该键的跳转同步调整。

本次归属修正的前端提交为 `0731419`。前端 typecheck/build 与完整浏览器回归通过：285 项页面、导航、布局、语言和权限检查，28 项真实表单/API 操作检查；额外覆盖长侧栏可见性、生涯去重保存、高级配置跳转，以及旧存储页面不再显示该表单。
