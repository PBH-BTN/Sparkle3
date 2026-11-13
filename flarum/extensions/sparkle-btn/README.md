# Sparkle BTN Profile for Flarum 2

该扩展把 Sparkle BTN 账户资料缓存到 Flarum，并在用户资料页和头像悬浮资料卡中显示：

- Sparkle BTN UID 和基础资料
- 最近 7 天四项流量数据及流量条
- Bytes 积分
- 流量排行榜和 Bytes 排行榜名次
- 自动维护的 `Sparkle BTN Users` 权限组

默认只有用户本人、管理员或拥有 `sparkleBtn.viewUserData` 权限的成员能看到缓存字段；用户还可以在
“设置 → 隐私”里关闭“向其他成员显示我的 Sparkle BTN 资料卡”。拥有 `sparkleBtn.viewHiddenUserData`
权限的成员可以忽略这个开关，但仍受 Sparkle 侧隐私设置限制。权限由 Flarum 管理后台的权限页控制。

## 环境要求

- Flarum `^2.0`
- FoF OAuth `^2.0`，并启用 GitHub provider
- PHP `^8.3`、`ext-json`、`ext-mbstring`
- Flarum scheduler 正常运行
- Sparkle 3 端的 Flarum 集成 token 已配置

## Sparkle 端配置

Sparkle 提供以下受限接口：

```text
GET /api/integrations/flarum/user-by-github?githubUid=<GitHub numeric UID>
X-Sparkle-Token: <secret>
```

登录 Sparkle 管理后台，进入“Flarum 集成”页面，填写并保存集成密钥。密钥会作为
敏感配置写入数据库，只允许写入，不会在后台回显，保存后立即生效。

可选地，也可以在环境变量或外部 `application.yaml` 中提供默认值：

```yaml
sparkle:
  flarum-integration:
    token: "replace-with-a-long-random-secret"
```

token 为空时接口返回 `503`；token 不匹配时返回 `401`。接口不会返回邮箱、GitHub access token 或其他私密字段。
后台保存的数据库值优先于环境默认值，不需要重启 Sparkle。

## 安装扩展

在 Flarum 根目录注册本地 path repository 并安装：

```bash
composer config repositories.sparkle-btn path /absolute/path/to/Sparkle3/flarum/extensions/sparkle-btn
composer require sparkle/btn-flarum:@dev
php flarum migrate
php flarum cache:clear
```

然后在 Flarum 管理后台启用 “Sparkle BTN Profile”。

启用后在扩展设置页填写：

- Sparkle BTN API endpoint：`https://<sparkle-host>/api/integrations/flarum/user-by-github`
- Integration token：与 Sparkle 后台“Flarum 集成”页面中保存的密钥一致
- 后台刷新间隔、单批用户数和请求超时

扩展会自动创建或复用名为 `Sparkle BTN Users` 的系统组，并将 BTN 绑定用户自动加入。BTN 绑定解除时用户会自动移出该组。扩展不会自动授予这个组任何权限，管理员可在 Flarum 权限页为它分配 `查看其他用户的 Sparkle BTN 资料数据` 权限。

## 计划任务

扩展每 15 分钟检查一次，按设置中的刷新间隔和批次大小同步已使用 GitHub 登录的 Flarum 用户。Flarum scheduler 必须由系统 cron 驱动：

```cron
* * * * * cd /path/to/flarum && php flarum schedule:run >> /dev/null 2>&1
```

手动同步：

```bash
php flarum sparkle-btn:sync
php flarum sparkle-btn:sync --force --limit=100
php flarum sparkle-btn:sync --github-uid=12345678
```

GitHub 登录时会立即执行一次同步；外部接口失败只会记录错误，不会阻断登录。下一次计划任务会重试，已成功缓存的数据不会被失败请求清空。

## API 响应

Sparkle 3 已实现该响应格式。未绑定时仍返回 HTTP 200：

```json
{
  "bound": false,
  "githubUid": "12345678",
  "btnUserId": null,
  "basicInfo": null,
  "traffic7d": null,
  "points": null,
  "rankings": null,
  "privacy": null,
  "generatedAt": "2026-11-13T12:00:00+08:00"
}
```

已绑定时主要字段如下：

```json
{
  "bound": true,
  "githubUid": "12345678",
  "btnUserId": "42",
  "basicInfo": {
    "id": "42",
    "nickname": "example",
    "registeredAt": "2025-01-01T00:00:00+08:00",
    "banned": false
  },
  "traffic7d": {
    "enabled": true,
    "windowDays": 7,
    "sentTrafficSelfReport": "1024",
    "receivedTrafficSelfReport": "2048",
    "sentTrafficOtherAck": "3072",
    "receivedTrafficOtherAck": "4096",
    "lastUpdatedAt": "2026-11-13T11:00:00+08:00"
  },
  "points": {
    "balanceBytes": "998877"
  },
  "rankings": {
    "traffic": {
      "rank": 12,
      "sentTrafficSelfReport": "1024",
      "receivedTrafficSelfReport": "2048",
      "sentTrafficOtherAck": "3072",
      "receivedTrafficOtherAck": "4096"
    },
    "points": {
      "rank": 34,
      "balanceBytes": "998877"
    }
  },
  "privacy": {
    "pointsVisible": true
  },
  "generatedAt": "2026-11-13T12:00:00+08:00"
}
```

字节值使用十进制字符串，避免 JavaScript 大整数精度丢失。客户端也兼容 `traffic_7d`、`btn_user_id` 等下划线字段名，以及 `{ "data": ... }` 包装。

## 隐私设置

接口按 Sparkle 的公开资料规则返回数据，Flarum 端不应绕过这些规则：

- 用户名：昵称审核未通过或未完成时不返回；用户把用户名可见性设为模糊时返回模糊后的昵称，设为完整时才返回原文。
- 积分：用户关闭积分可见性后，`points`、`rankings.points` 均不返回，`privacy.pointsVisible` 为 `false`。Flarum 卡片会显示“已隐藏”，不会用 0 代替。
- 头像：卡片本来就不展示 Sparkle 头像，因此不涉及。
- 资料审核状态：昵称处于待审核或已拒绝状态时不返回昵称。

同步是缓存的，隐私设置的修改要等该用户下一次同步（最长一个 `refresh interval`）才会反映到 Flarum；需要立即生效时可在 Flarum 侧手动执行 `php flarum sparkle-btn:sync --force --github-uid=<uid>`。

### Flarum 侧的卡片可见性

除了 Sparkle 的数据隐私，Flarum 还有两层独立控制：

- 用户偏好 `sparkleBtnShowCard`（设置 → 隐私 → “向其他成员显示我的 Sparkle BTN 资料卡”，默认开启）：关闭后，其他成员在资料页和头像卡中看不到该用户的 BTN 卡片，本人仍然可见，卡片上会标注“仅自己可见”。
- 权限 `sparkleBtn.viewUserData`：控制成员是否能看到其他人的 BTN 数据；`sparkleBtn.viewHiddenUserData` 则可以忽略上面的用户偏好强制查看，但它不能绕过 Sparkle 返回数据时的隐私过滤（例如隐藏的积分仍然显示“已隐藏”）。

这两个开关只影响 Flarum 的展示，不会修改 Sparkle 侧的任何设置。

## 构建前端

仓库已包含生产构建产物 `js/dist/forum.js` 和 `js/dist/admin.js`。修改前端后运行：

```bash
cd flarum/extensions/sparkle-btn/js
pnpm install
pnpm run typecheck
pnpm run build
```

## 打包发布

发布包只需要 PHP 服务端文件、语言包和已构建的 `js/dist`，不需要 `js/node_modules`、`js/src`
或前端构建配置。在仓库根目录用 `git archive` 生成（会应用 `.gitattributes` 的导出规则）：

```bash
git archive --format=zip --prefix=sparkle-btn/ \
  -o flarum/extensions/sparkle-btn.zip HEAD:flarum/extensions/sparkle-btn
```

也可以在 `flarum/extensions/sparkle-btn` 目录内运行 `composer archive`，`composer.json` 的
`archive.exclude` 已排除这些开发文件。

手工打包时只收集：`extend.php`、`composer.json`、`README.md`、`.gitattributes`、`src/`、
`migrations/`、`locale/`、`less/`、`js/dist/`。

正常发布包在 100 KB 以内；如果明显更大，通常是误把 `js/node_modules` 打了进去。用
Composer path repository 安装时如果采用复制而不是符号链接，也应先移除 `js/node_modules`。

## 隐私与部署提示

- Sparkle token 只保存在 Flarum 服务端设置中，不会序列化到论坛前端。
- 缓存字段的读取权限在服务端 API 层判断，不能通过修改浏览器请求绕过。
- 拥有查看权限的用户可以查看其他已绑定用户的 BTN UID、流量、积分和排行榜。
- 建议通过 HTTPS 部署 Sparkle 和 Flarum，并限制 Flarum 服务器到 Sparkle 接口的网络访问。
