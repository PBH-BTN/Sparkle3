# Sparkle 3

## 数据库与自动迁移

数据库使用 Flyway 管理，应用启动时会自动检查并执行 `classpath:db/migration` 中尚未应用的
迁移。全新数据库从 `V1__initial_schema.sql` 建库；已经有数据的生产库会在
`FlywayMigrationConfig` 中识别当前结构版本并建立基线，不会重新执行旧版建表或大表迁移。

生产环境需要 PostgreSQL、TimescaleDB、`pg_trgm` 扩展，以及具备创建 schema 对象、
索引和 TimescaleDB policy 权限的数据库账号。`timescaledb` 扩展需由数据库管理员预装，
应用迁移只会执行 `CREATE EXTENSION IF NOT EXISTS`。

升级顺序如下：

1. 备份数据库，确认 `timescaledb` 扩展可用。
2. 先部署一次新版本。Flyway 会在 PostgreSQL advisory lock 保护下自动建立/更新
   `flyway_schema_history`，无需手工执行 SQL。
3. 查看启动日志。已有库接管会记录 `Adopting existing Sparkle3 schema into Flyway at
   legacy version ...`；之后的增量迁移由 Flyway 正常记录。
4. 后续 schema 变更只需新增 `V<version>__<description>.sql`，禁止修改已经发布的迁移文件
   checksum。

如果旧库还未完成 TimescaleDB 转换（`ban_history` 不是 hypertable），应用会拒绝自动
接管并给出提示。此时需要先使用 `db_sql/` 中保留的历史脚本完成一次
`v20_convert_to_timescaledb.sql` 到 `v22_fuck_index.sql` 的升级，再启动新版本。

## 前端开发

前端使用 Vue 3、TypeScript、Vue Router、Pinia 与 Arco Design Vue。依赖管理统一使用 pnpm：

```bash
cd frontend
pnpm install
pnpm dev
```

开发服务器默认监听 `http://localhost:5173`，并将 `/api`、OAuth 与头像请求代理到
`http://localhost:7799`。前端支持简体中文和英文，可在导航栏实时切换，语言选择会保存在浏览器本地。

生产构建由 Gradle 自动调用 pnpm，并把 Vite 产物打入 Spring Boot jar：

```bash
pnpm --dir frontend build
./gradlew build
```

单独验证前端可运行：

```bash
pnpm --dir frontend typecheck
pnpm --dir frontend build
```

## 后台管理

角色为 `admin` 的用户可从右上角用户菜单进入 `/admin/dashboard`。角色为 `moderator`
的协管用户可从右上角进入受限后台；管理员自动继承协管的全部权限。后台包含：

- 仪表盘：主机 CPU、内存、磁盘实时指标，7 天趋势，JVM 信息及功能模块统计。
- 用户管理：搜索、创建、编辑、停用/恢复和删除用户。删除用户前必须先删除其用户应用。
- 用户资料：支持通过 `/user/profile/<uid>` 查看其他用户；头像、用户名与积分分别遵循对方独立隐私选项。
- 站内信：导航栏头像左侧的收件箱集中展示用户消息和系统通知。用户间的普通站内信以对话列表展示，包含已发送与已接收的消息，支持分页查看历史和站内回复；系统通知与群发消息独立展示。删除收到的消息仅影响本人收件箱，发送方仍可查看其发送记录。普通用户每天最多发起 15 次站内信发送；管理员和协管可向全站群发或指定 UID 发送，支持 Sparkle BTN 系统身份与强制邮件提醒。
- 通知提醒：用户可分别控制每个通知类别的站内信和邮件提醒。法律合规与账户安全通知不可关闭；一般通知、更新日志和日常公告默认关闭邮件提醒，其余类别默认开启。所有类别默认开启站内信，已保存的个人选择不受默认值调整影响。
- 用户联系邮件：原有纯文本联系邮件审核能力保留；后台可配置邮件标题/正文模板并查询发送人、目标人、时间、IP 和 User-Agent 审计记录。
- 用户应用管理：搜索、创建、转移所有者、停用、恢复、重置密钥和删除。
- 手动规则：维护 `rules` 表中的 IP 黑名单、Peer ID、客户端名称、端口和脚本规则，并可预览生成的 IP 黑名单。
- 功能模块：集中维护自动分析模块的开关、Cron、统计窗口、流量阈值和投票阈值，保存后动态重建调度任务。
- 任务状态与计划：按计划任务、触发任务和启动任务查看运行结果，调整受模块管理的 Cron，并即时启用或停用对应模块。接入方式见 [后台任务系统使用与接入指南](docs/task-scheduling.md)。
- 运行设置：热更新协议版本、IP 查询 PoW、客户端发现开关，以及最近 7 日流量统计的启用状态、刷新间隔、统计窗口和手动刷新。
- 公告管理：支持“全局最顶公告区”和“首页 Hero 公告区”两个位置，提供绿、黄、蓝、红四种 Arco 警告样式、中英文内容、定时生效、排序和用户关闭。默认不发送站内信；编辑器中开启“同时发送站内信”后，保存已启用的公告时会立即按重大/日常公告类别发送一次通知，不随公告展示时间延后。管理 API 的可选请求字段 `sendInbox` 默认 `false`，仅对本次保存生效，不存储为公告属性；列表中的直接发布操作不发送站内信。
- IP 评论管理：检索、编辑、通过、拒绝、隐藏和恢复 IP / CIDR 评论区评论，并可手动重新提交 AI 审核；计划任务会自动补审待审核和审核失败的评论。
- 站内信群发：管理员和协管可选择全部活跃用户或指定 UID、通知类别、发送身份及是否强制邮件提醒。
- Navbar 导航管理：内置项可启停、改名、选择图标和排序；自定义项支持中英文名称、站内路径或外部链接。
- 首页统计：使用 ECharts 展示 Peer ID、客户端、GeoIP 城市、IPv4/IPv6 趋势、BitTorrent 流量承载，以及 BTN 客户端版本和地址栈分布。后台“首页数据刷新”的各模块开关统一控制自动刷新与首页显示，关闭后重新加载首页即可隐藏对应统计卡片或图表，并停止自动刷新。BitTorrent 流量模块同时禁止手动更新，其他模块仍可手动刷新。`GET /api/ui/home` 的 `enabledModules` 返回各模块的启用状态，不包含后台运行详情。
- 存储与策略：查看普通表与 TimescaleDB hypertable 占用，显示压缩/列存前后大小和 chunk 状态，管理自动压缩、列存和自动淘汰策略。
- `ban_history` 维护：按 cutoff 预览并删除完整旧 chunks；删除需要输入确认文本，避免误操作。
- 版本提醒：基于客户端产品名、User-Agent 和 NPM 语义化版本范围配置邮件通知，支持启停、编辑、匹配测试、真实测试邮件和发送记录。
- 会员平台与许可证：在“捐赠支持”页统一配置爱发电、Patreon、Ko-fi、面包多的官方接入凭据，
  查看同步运行记录、远端缺失对账、交易账本和已签发许可证，并维护所有平台共用的 PBHLicense 签发规则。

主机指标每分钟采样一次并保存到 Redis key `sparkle:admin:metrics:host:history`，保留 8 天。
后台运行设置保存在 PostgreSQL 表 `app_config_entries`，并保留 `application.yaml` 中的值作为默认值。
数据库、Redis、OAuth、邮件、Sentry 和令牌等敏感配置不会暴露在后台。

管理 API 位于 `/api/admin/**`，除 Spring Security 的 `admin` / `moderator` authority 校验外，
还会在每次请求时重新检查数据库中的最新角色与停用状态。协管仅能访问任务状态与计划、公告管理、
站内信群发、应用管理、GitHub 规则同步、BTN 管理、封禁模块管理和手动规则模块。

存储统计会自动识别 TimescaleDB 2.18 以前的 `hypertable_compression_stats` 和 2.18+ 的
`hypertable_columnstore_stats`。hypertable 使用 `hypertable_detailed_size` 汇总 chunk、
索引和 TOAST 占用；普通 PostgreSQL 表使用系统目录和统计信息计算占用。TimescaleDB 会按
chunk 删除历史数据，因此手动 cutoff 的实际边界可能早于所选时间。

原先写死在 `UserappServiceImpl` 中的 PeerBanHelper 9.3.0 安全提醒已迁移为版本提醒规则。
首次启动会在 Redis 中创建对应的默认规则，之后可在后台自由修改或删除。规则保存在
`sparkle:admin:version-alerts:rules`，发送记录保存在
`sparkle:admin:version-alerts:deliveries`。

积分不再发放“一次性积分”和“新 IP 地址”奖励。升级到 Flyway `V58` 会删除这两类积分来源的
配置和历史分类计数，并移除 `point_known_ips`、`point_daily_ips` 及对应去重函数。
“命中规则的 IP”改为每条有效命中直接计奖，仍受每日上限约束，不再按 IP 去重；
`point_claims` 继续用于活跃应用每日领取。

## 捐赠支持与 PBHLicense

爱发电、Patreon、Ko-fi、面包多都是“捐赠支持”的组成部分，统一写入 `member_transactions`
账本，用户通过 `member_platform_bindings` 关联账号。用户设置中只有一个“捐赠支持与许可证”
入口，后台也只有一个“捐赠支持”页，不再单独展示爱发电。
许可证槽位由 `PbhLicenseService` 按同一套规则计算：商品交易按订单产生槽位，捐赠/订阅按
结算货币累计，每达到配置金额产生一个槽位；签发规则由所有平台共用。

各平台接入方式：

- **爱发电**：在后台“捐赠支持”页填写用户 ID 和 Access Key。订单通过
  `product_type` 自动区分商品与赞助方案，不需要再手工维护 Plan ID 列表；商品订单直接产生
  许可证槽位。
- **Patreon**：填写 Patreon 开发者门户中的 Creator Access Token（需要
  `campaigns.members` 权限）。Campaign ID 留空时自动发现，可按 Campaign 币种结算。Patreon
  公开 API 只有会员订阅，没有商品接口，因此会员的累计支持金额（
  `campaign_lifetime_support_cents`）计入捐赠累计。可选填写 Webhook Secret 接收
  `members:*` 实时事件；不配置时依靠定时全量同步。
- **Ko-fi**：Ko-fi 没有公开的历史查询 API，只提供支付 Webhook。后台填写 Ko-fi
  “API/Webhooks → Advanced”中的 Verification Token，并把页面给出的 Webhook 地址填到
  Ko-fi。启用后收到的捐赠、订阅付款和商品订单会实时入账；启用前的历史交易无法补拉。
- **面包多**：填写“编辑资料 → 开发设置”中的开发者 Key。定时任务通过
  `https://x.mbd.pub/api/order-list` 同步全部作品订单。面包多 API 不返回买家标识，因此用户
  按订单号认领：每笔订单单独绑定，同一个 Sparkle 用户可以认领多个订单（Flyway `V59` 仅对
  面包多放宽了“每个平台只能绑定一个账号”的唯一约束）。
- **链动小铺**：没有公开的订单或销售 API，后台保留该平台的说明但不提供自动同步与绑定。

平台的商品与捐赠会分别计数，但“累计已捐赠”汇总该账号全部已完成交易。同步任务每 6 小时
运行一次，也可在后台按平台立即同步；每次同步都会记录运行结果、远端缺失和交易变更审计。

## Flarum 2 集成

仓库内的 [`flarum/extensions/sparkle-btn`](flarum/extensions/sparkle-btn/README.md)
提供 Flarum 2 + FoF OAuth 插件，以及 Sparkle 端的 GitHub UID 查询接口。插件会在
GitHub 登录和计划任务中缓存 BTN UID、7 日流量、Bytes 积分及两类排行榜，并按权限
在 Flarum 用户资料页和头像悬浮卡中展示；绑定用户同时自动加入专用权限组。

在 Sparkle 后台“Flarum 集成”页面保存集成密钥即可；配置会写入数据库敏感项并立即生效。

邮件发件人和显示名可通过以下配置修改：

```yaml
sparkle:
  version-alert:
    from-address: "sparkle@pbh-btn.com"
    from-name: "SparkleBTN 系统通知"
```

## 最近 7 日流量统计

统计任务默认每 60 分钟检查一次是否到期，统计窗口为最近 7 天。管理员可在
`/admin/settings` 调整开关、刷新间隔与窗口天数，并点击“立即刷新全部用户”触发一次后台重算。
页面会显示最近完成时间、耗时、更新用户数、触发方式、失败原因和数据是否过期。
关闭统计功能后会停止自动与手动刷新，同时隐藏顶部流量条和排行榜入口，排行榜 API 也会拒绝访问。

统计按 `last_time_seen` 而非 `first_time_seen` 判断窗口，避免漏掉最近仍活跃、但首次出现时间较早的
Peer。每次刷新只扫描一次 `swarm_tracker`，先将最近窗口按用户和 Peer IP 聚合到物化快照，
再从同一快照计算自主汇报与授权确认流量。心跳 IP 是单个 host 地址，因此使用等值连接让
PostgreSQL 选择 hash join，避免重复 CIDR 嵌套循环。

相关迁移已经纳入 Flyway，启动时会自动执行，不再需要手工运行 `psql -f`。首次为大型生产库
创建 hypertable 索引或连续聚合仍可能产生较多 I/O，建议在维护窗口部署。上线前建议
先在测试库使用 `EXPLAIN (ANALYZE, BUFFERS)` 检查 `refreshUserSwarmStatistics` 的执行计划与索引命中情况。

首页网络统计默认查询最近 7 天的封禁数据。Flyway `V26` 会创建 Peer ID、客户端和
GeoIP/IP 协议栈三个 TimescaleDB continuous aggregate，并启用列存压缩与每小时的增量刷新策略；
首页查询优先读取聚合结果，迁移尚未执行或聚合不可用时自动回退原始表。查询结果在 Redis 中缓存
5 分钟，并支持 1、7、14、30 天以及“仅封禁”与“封禁 + 种群”数据源切换。
三个首页聚合默认只保留最近 35 天，TimescaleDB retention policy 会自动删除更早的 chunks。

BTN 同伴 GeoIP 分布使用 `userapps_heartbeat.geoip`，统计最近 24 小时地址的心跳 IP 城市归属。
新增列完成后，历史地址会在下一次心跳上报时补全 GeoIP，活跃客户端通常会在数秒内恢复覆盖。

## 数据库配置迁移

应用启动时会自动扫描 classpath 和工作目录中的 `application.yaml`、`application.yml`
及当前激活 profile 对应的 YAML 文件，将缺失的非数据源配置项写入 PostgreSQL 表
`app_config_entries`。已有数据库值不会被后续 YAML 扫描覆盖。

启动期配置加载顺序为：

1. 命令行参数。
2. 环境变量。
3. `app_config_entries` 数据库值。
4. YAML 配置默认值。

数据源连接配置 `spring.datasource.*` 与数据库迁移配置 `spring.flyway.*` 永远不会迁移到
该表，避免后台配置绕过启动期结构升级。敏感项会迁移，但后台 API 只返回隐藏状态，不允许
读取或修改。普通配置修改在重启后生效；当前运行设置与流量统计开关
仍可在“运行设置”中立即应用。后台入口为 `/admin/database-config`，也可手动触发一次
YAML 扫描。旧版保存在 Redis `sparkle:admin:runtime-config` 中的热更新值会在首次数据库
迁移时自动兼容导入。

运行期批量读取非敏感业务配置时会优先使用 Redis 缓存，未命中再读取 `app_config_entries`
并回填缓存，缓存有效期为 5 分钟。数据库仍是配置事实源；配置写入提交后会失效对应缓存，
Redis 不可用时直接回退数据库。敏感配置不会写入这份缓存。

`app_config_entries` 表由 Flyway `V23` 创建。数据库账号只有 DML 权限时，需要数据库管理员
提前授予 schema DDL 权限，否则应用会按设计拒绝启动，避免运行在结构不完整的数据库上。

首次启用后台时，可由数据库管理员为已有账户授予角色：

```sql
UPDATE "user" SET role = 'admin' WHERE id = <user-id>;
UPDATE "user" SET role = 'moderator' WHERE id = <user-id>;
```

隐私设置自 Flyway `V46` 起保存在独立的 `user_privacy_settings` 表，支持原始/模糊/隐藏头像、
完整/模糊用户名以及显示/隐藏积分。旧 `privacy_level` 列只用于迁移已有用户，不再参与新界面或公开资料计算。
