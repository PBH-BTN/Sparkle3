# Sparkle 3

## PostgreSQL 扩展

需要 pg_partman, pg_cron 和 timescaledb。

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

角色为 `admin` 的用户可从右上角用户菜单进入 `/admin/dashboard`。后台包含：

- 仪表盘：主机 CPU、内存、磁盘实时指标，7 天趋势，JVM 信息及功能模块统计。
- 用户管理：搜索、创建、编辑、停用/恢复和删除用户。删除用户前必须先删除其用户应用。
- 用户应用管理：搜索、创建、转移所有者、停用、恢复、重置密钥和删除。
- 手动规则：维护 `rules` 表中的 IP 黑名单、Peer ID、客户端名称、端口和脚本规则，并可预览生成的 IP 黑名单。
- 运行设置：热更新协议版本、IP 查询 PoW、客户端发现开关，以及最近 7 日流量统计的启用状态、刷新间隔、统计窗口和手动刷新。
- 公告管理：发布全站顶部公告，支持绿、黄、蓝、红四种 Arco 警告样式、中英文内容、定时生效、排序和用户关闭。
- Navbar 导航管理：内置项可启停、改名、选择图标和排序；自定义项支持中英文名称、站内路径或外部链接。
- 首页统计：使用 ECharts 展示 Peer ID、客户端、GeoIP 城市、IPv4/IPv6 趋势，以及 BTN 客户端版本和地址栈分布。
- 存储与策略：查看普通表与 TimescaleDB hypertable 占用，显示压缩/列存前后大小和 chunk 状态，管理自动压缩、列存和自动淘汰策略。
- `ban_history` 维护：按 cutoff 预览并删除完整旧 chunks；删除需要输入确认文本，避免误操作。
- 版本提醒：基于客户端产品名、User-Agent 和 NPM 语义化版本范围配置邮件通知，支持启停、编辑、匹配测试、真实测试邮件和发送记录。

主机指标每分钟采样一次并保存到 Redis key `sparkle:admin:metrics:host:history`，保留 8 天。
后台运行设置保存在 PostgreSQL 表 `app_config_entries`，并保留 `application.yaml` 中的值作为默认值。
数据库、Redis、OAuth、邮件、Sentry 和令牌等敏感配置不会暴露在后台。

管理 API 位于 `/api/admin/**`，除 Spring Security 的 `admin` authority 校验外，还会在每次请求时重新检查数据库中的最新角色与停用状态。

存储统计会自动识别 TimescaleDB 2.18 以前的 `hypertable_compression_stats` 和 2.18+ 的
`hypertable_columnstore_stats`。hypertable 使用 `hypertable_detailed_size` 汇总 chunk、
索引和 TOAST 占用；普通 PostgreSQL 表使用系统目录和统计信息计算占用。TimescaleDB 会按
chunk 删除历史数据，因此手动 cutoff 的实际边界可能早于所选时间。

原先写死在 `UserappServiceImpl` 中的 PeerBanHelper 9.3.0 安全提醒已迁移为版本提醒规则。
首次启动会在 Redis 中创建对应的默认规则，之后可在后台自由修改或删除。规则保存在
`sparkle:admin:version-alerts:rules`，发送记录保存在
`sparkle:admin:version-alerts:deliveries`。

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

生产库首次升级时请执行：

```bash
psql -f src/main/resources/db_sql/v24_swarm_statistics_and_announcements.sql
psql -f src/main/resources/db_sql/v25_navigation_items.sql
psql -f src/main/resources/db_sql/v26_home_statistics.sql
psql -f src/main/resources/db_sql/v27_home_statistics_retention.sql
psql -f src/main/resources/db_sql/v28_home_statistics_indexes.sql
psql -f src/main/resources/db_sql/v29_heartbeat_geoip.sql
```

TimescaleDB hypertable 不支持 `CREATE INDEX CONCURRENTLY`，因此请在维护窗口执行该脚本。上线前建议
先在测试库使用 `EXPLAIN (ANALYZE, BUFFERS)` 检查 `refreshUserSwarmStatistics` 的执行计划与索引命中情况。

首页网络统计默认查询最近 7 天的封禁数据。`v26_home_statistics.sql` 会创建 Peer ID、客户端和
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

数据源连接配置 `spring.datasource.*` 永远不会迁移到该表。敏感项会迁移，但后台 API
只返回隐藏状态，不允许读取或修改。普通配置修改在重启后生效；当前运行设置与流量统计开关
仍可在“运行设置”中立即应用。后台入口为 `/admin/database-config`，也可手动触发一次
YAML 扫描。旧版保存在 Redis `sparkle:admin:runtime-config` 中的热更新值会在首次数据库
迁移时自动兼容导入。

自动迁移需要数据库账号具备创建表和索引的权限。如果生产账号只有 DML 权限，可先由数据库
管理员执行 `src/main/resources/db_sql/v23_app_config_entries.sql`，之后应用会自动填充缺失配置。

首次启用后台时，可由数据库管理员为已有账户授予角色：

```sql
UPDATE "user" SET role = 'admin' WHERE id = <user-id>;
```
