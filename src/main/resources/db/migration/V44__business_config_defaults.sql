-- Business configuration defaults are seeded once. Sensitive and identity-bearing
-- production values are represented by placeholders and must be supplied in the UI.
-- Only keys explicitly present in the production YAML are allowed to replace existing values.
WITH defaults(config_key, config_value, value_type, config_group, sensitive, hot_reload, overwrite_existing) AS (
    VALUES
        ('sparkle.admin.user-ip-warning.cron', '-', 'string', 'sparkle.admin', false, true, true),
        ('sparkle.admin.user-ip-warning.duration', '1209600000', 'number', 'sparkle.admin', false, true, true),
        ('sparkle.analyse.ban-peer-distribution.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.ban-peer-distribution.schedule', '0 20 2 * * *', 'string', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.btnbypass-userapps.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.btnbypass-userapps.schedule', '0 */20 * * * *', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.concurrent-download-analyse.duration', '28800000', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.concurrent-download-analyse.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.concurrent-download-analyse.schedule', '0 10 */3 * * *', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.concurrent-download-analyse.threshold-concurrent', '80', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.concurrent-download-analyse.threshold-userapps', '3', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.datacenter-highrisk-analyse.duration', '1209600000', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.datacenter-highrisk-analyse.enabled', 'false', 'boolean', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.datacenter-highrisk-analyse.schedule', '-', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.geoip-city-blacklist.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.geoip-city-blacklist.schedule', '0 0 4 * * *', 'string', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.gopeeddev-identity-analyse.duration', '1209600000', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.gopeeddev-identity-analyse.enabled', 'false', 'boolean', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.gopeeddev-identity-analyse.schedule', '-', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.mass-download-monitor.duration', '86400000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.mass-download-monitor.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.mass-download-monitor.ipv4-prefix', '32', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.mass-download-monitor.ipv6-prefix', '56', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.mass-download-monitor.schedule', '0 0 * * * *', 'string', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.mass-download-monitor.threshold-download', '10737418240', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.overdownload-analyse.duration', '432000000', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.overdownload-analyse.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.overdownload-analyse.materialized-view-refresh', 'false', 'boolean', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.overdownload-analyse.schedule', '0 23 */2 * * *', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.overdownload-analyse.threshold-ratio', '3.2', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.overdownload-analyse.threshold-traffic', '15000000000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.overdownload-analyse.use-materialized-view', 'false', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic-rules.dynamic-download-mib', '512', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic-rules.dynamic-download-points', '50', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic-rules.dynamic-hot-peer-points', '100', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic-rules.dynamic-hot-peer-top', '50', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic-rules.dynamic-seed-upload-mib', '200', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic-rules.dynamic-seed-upload-points', '180', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic-rules.dynamic-torrent-size-gib', '1', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-dynamic.schedule', '0 5 * * * *', 'string', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.active-app-points', '5000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.app-online-gap-minutes', '15', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.app-online-points', '500', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.ban-history-daily-cap', '2000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.ban-history-points', '3', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.keepalive-daily-cap', '2000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.keepalive-points', '1', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.login-daily-cap', '1000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.login-points', '1000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.new-ip-daily-cap', '2000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.new-ip-points', '20', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.rule-hit-ip-daily-cap', '2000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.rule-hit-ip-points', '10', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.rules-fetch-daily-cap', '2000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.rules-fetch-points', '2', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.swarm-tracker-daily-cap', '2000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.swarm-tracker-points', '3', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.wasted-upload-daily-cap', '3000', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.wasted-upload-mib', '1', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-fixed-rules.wasted-upload-points-per-mib', '10', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-once-rules.client-discovery-points', '4', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-once-rules.new-torrent-points', '500', 'number', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.active-app-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.app-online-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.ban-history-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.client-discovery-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.dynamic-download-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.dynamic-hot-peer-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.dynamic-seed-upload-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.keepalive-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.login-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.new-ip-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.new-torrent-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.rule-hit-ip-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.rules-fetch-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.swarm-tracker-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.points-settings.wasted-upload-enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.rain000-identity-analyse.duration', '1209600000', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.rain000-identity-analyse.enabled', 'false', 'boolean', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.rain000-identity-analyse.schedule', '-', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.random-identity-analyse.duration', '1209600000', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.random-identity-analyse.enabled', 'false', 'boolean', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.random-identity-analyse.schedule', '-', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.swarm-peer-distribution.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.swarm-peer-distribution.schedule', '0 10 * * * *', 'string', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.untrusted-vote.duration', '604800000', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.enabled', 'true', 'boolean', 'sparkle.analyse', false, true, false),
        ('sparkle.analyse.untrusted-vote.include-modules', 'com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker,com.ghostchu.peerbanhelper.module.impl.rule.MultiDialingBlocker', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.ipv4.enable', 'true', 'boolean', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.ipv4.min-ban-count', '5', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.ipv4.min-userapps-vote', '5', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.ipv6.enable', 'true', 'boolean', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.ipv6.min-ban-count', '5', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.ipv6.min-userapps-vote', '5', 'number', 'sparkle.analyse', false, true, true),
        ('sparkle.analyse.untrusted-vote.schedule', '0 */40 * * * *', 'string', 'sparkle.analyse', false, true, true),
        ('sparkle.banhistory.refresh-module-name-cache-cron', '0 0 */2 * * *', 'string', 'sparkle.banhistory', false, true, false),
        ('sparkle.captcha.pow.algorithm', 'SHA-256', 'string', 'sparkle.captcha', false, true, true),
        ('sparkle.captcha.pow.difficulty-bits', '6', 'number', 'sparkle.captcha', false, true, true),
        ('sparkle.captcha.pow.endpoint', '{rooturl}/ping/captcha/createSession', 'string', 'sparkle.captcha', false, true, true),
        ('sparkle.chn-root-url', 'https://sparkle.ghostchu.com', 'string', 'sparkle.chn-root-url', false, true, true),
        ('sparkle.client-discovery.enabled', 'true', 'boolean', 'sparkle.client-discovery', false, true, true),
        ('sparkle.github-sync.branch-name', 'main', 'string', 'sparkle.github-sync', false, true, true),
        ('sparkle.github-sync.org-name', 'example', 'string', 'sparkle.github-sync', false, true, true),
        ('sparkle.github-sync.personal-access-token', 'foobar', 'string', 'sparkle.github-sync', true, true, true),
        ('sparkle.github-sync.repository-name', 'example-repository', 'string', 'sparkle.github-sync', false, true, true),
        ('sparkle.github-sync.schedule', '0 30 * * * *', 'string', 'sparkle.github-sync', false, true, true),
        ('sparkle.homepage-statistics-refresh.modules.active-apps.enabled', 'true', 'boolean', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.active-apps.refresh-interval-minutes', '30', 'number', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.ban-history.enabled', 'true', 'boolean', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.ban-history.refresh-interval-minutes', '10', 'number', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.enabled', 'true', 'boolean', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.refresh-interval-minutes', '60', 'number', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.companion-charts.enabled', 'true', 'boolean', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.companion-charts.refresh-interval-minutes', '5', 'number', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.network-charts.enabled', 'true', 'boolean', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.network-charts.refresh-interval-minutes', '5', 'number', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.swarm-tracker.enabled', 'true', 'boolean', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.swarm-tracker.refresh-interval-minutes', '20', 'number', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.torrents.enabled', 'true', 'boolean', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.homepage-statistics-refresh.modules.torrents.refresh-interval-minutes', '40', 'number', 'sparkle.homepage-statistics-refresh', false, true, false),
        ('sparkle.measure.enable', 'false', 'boolean', 'sparkle.measure', false, true, true),
        ('sparkle.measure.expired-after', '31104000000', 'number', 'sparkle.measure', false, true, true),
        ('sparkle.measure.globalping-result-retrieve-cron', '0 */5 * * * *', 'string', 'sparkle.measure', false, true, true),
        ('sparkle.measure.globalping-start-measure-cron', '0 49 * * * *', 'string', 'sparkle.measure', false, true, true),
        ('sparkle.measure.globalping-token', 'foobar', 'string', 'sparkle.measure', true, true, true),
        ('sparkle.measure.prefix-length.ipv4', '24', 'number', 'sparkle.measure', false, true, true),
        ('sparkle.measure.prefix-length.ipv6', '48', 'number', 'sparkle.measure', false, true, true),
        ('sparkle.ping.config.max-protocol-version', '20', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.config.min-protocol-version', '20', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.heartbeat.cleanup-before', '604800000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.heartbeat.cleanup-cron', '0 0 0 * * *', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.heartbeat.endpoint', 'https://sparkle.pbh-btn.com/ping/heartbeat', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.heartbeat.interval', '1800000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.heartbeat.multi-if', 'true', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.heartbeat.pow-captcha', 'false', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.heartbeat.queue.batch-size', '2000', 'number', 'sparkle.ping', false, true, false),
        ('sparkle.ping.heartbeat.queue.flush-interval-ms', '1000', 'number', 'sparkle.ping', false, true, false),
        ('sparkle.ping.heartbeat.queue.initial-delay-ms', '1000', 'number', 'sparkle.ping', false, true, false),
        ('sparkle.ping.heartbeat.queue.max-size', '100000', 'number', 'sparkle.ping', false, true, false),
        ('sparkle.ping.heartbeat.random-initial-delay', '3000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.query-ip.endpoint', '{rooturl}/ping/queryIp', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.query-ip.iframe-endpoint', '{rooturl}/ping/queryIp/widget', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.query-ip.pow-captcha', 'false', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.reconfigure.interval', '3600000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.reconfigure.random-initial-delay', '600000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-allowlist.endpoint', '{rooturl}/ping/ruleIpAllowlist', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-allowlist.interval', '600000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-allowlist.pow-captcha', 'false', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-allowlist.random-initial-delay', '15000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-denylist.endpoint', '{rooturl}/ping/ruleIpDenylist', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-denylist.interval', '600000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-denylist.pow-captcha', 'false', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-ip-denylist.random-initial-delay', '15000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-peer-identity.endpoint', '{rooturl}/ping/rulePeerIdentity', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-peer-identity.interval', '2700000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-peer-identity.pow-captcha', 'false', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.rule-peer-identity.random-initial-delay', '15000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-banhistory.endpoint', '{rooturl}/ping/syncBanHistory', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-banhistory.interval', '900000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-banhistory.pow-captcha', 'false', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-banhistory.random-initial-delay', '15000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-swarm.data-retention-cron', '0 0 */2 * * *', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-swarm.data-retention-time', '259200000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-swarm.endpoint', '{rooturl}/ping/syncSwarm', 'string', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-swarm.interval', '900000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-swarm.pow-captcha', 'false', 'boolean', 'sparkle.ping', false, true, true),
        ('sparkle.ping.sync-swarm.random-initial-delay', '15000', 'number', 'sparkle.ping', false, true, true),
        ('sparkle.points.time-zone', 'Asia/Shanghai', 'string', 'sparkle.points', false, true, false),
        ('sparkle.query.query-ip.bans-counting-duration', '604800000', 'number', 'sparkle.query', false, true, true),
        ('sparkle.query.query-ip.heartbeat-query-duration', '604800000', 'number', 'sparkle.query', false, true, true),
        ('sparkle.query.query-ip.include-modules', 'com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker,com.ghostchu.peerbanhelper.module.impl.rule.MultiDialingBlocker,com.ghostchu.peerbanhelper.module.impl.rule.AutoRangeBan,com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline', 'string', 'sparkle.query', false, true, true),
        ('sparkle.query.query-ip.swarms-counting-duration', '604800000', 'number', 'sparkle.query', false, true, true),
        ('sparkle.query.query-ip.torrents-counting-duration', '604800000', 'number', 'sparkle.query', false, true, true),
        ('sparkle.query.query-ip.traffic-measure-duration', '604800000', 'number', 'sparkle.query', false, true, true),
        ('sparkle.ranking.weight.user-swarm-statistics.received-traffic-other-ack', '5', 'number', 'sparkle.ranking', false, true, true),
        ('sparkle.ranking.weight.user-swarm-statistics.received-traffic-self-report', '1', 'number', 'sparkle.ranking', false, true, true),
        ('sparkle.ranking.weight.user-swarm-statistics.sent-traffic-other-ack', '20', 'number', 'sparkle.ranking', false, true, true),
        ('sparkle.ranking.weight.user-swarm-statistics.sent-traffic-self-report', '10', 'number', 'sparkle.ranking', false, true, true),
        ('sparkle.root-url', 'https://sparkle.pbh-btn.com', 'string', 'sparkle.root-url', false, true, true),
        ('sparkle.security.zip-bomb-threshold', '33554432', 'number', 'sparkle.security', false, true, false),
        ('sparkle.swarm-statistics-track.enabled', 'false', 'boolean', 'sparkle.swarm-statistics-track', false, true, true),
        ('sparkle.swarm-statistics-track.refresh-interval-minutes', '60', 'number', 'sparkle.swarm-statistics-track', false, true, false),
        ('sparkle.swarm-statistics-track.window-days', '7', 'number', 'sparkle.swarm-statistics-track', false, true, true),
        ('sparkle.user-avatar.cache-directory', 'data/avatar-cache', 'string', 'sparkle.user-avatar', false, true, false),
        ('sparkle.user-avatar.cache-duration', '2592000000', 'number', 'sparkle.user-avatar', false, true, false),
        ('sparkle.user-avatar.cleanup-cron', '0 30 4 * * *', 'string', 'sparkle.user-avatar', false, true, false),
        ('sparkle.user-contact-email.content-template', '{{senderName}}（UID {{senderId}}）通过 Sparkle BTN 向你发送了一条消息：

{{content}}

--
这是一封由用户发起并通过 Sparkle BTN 发送的邮件，请勿直接回复。
', 'string', 'sparkle.user-contact-email', false, true, false),
        ('sparkle.user-contact-email.from-address', 'example@example.com', 'string', 'sparkle.user-contact-email', false, true, false),
        ('sparkle.user-contact-email.from-name', 'Sparkle BTN 用户邮件', 'string', 'sparkle.user-contact-email', false, true, false),
        ('sparkle.user-contact-email.subject-template', '[Sparkle BTN] {{title}}', 'string', 'sparkle.user-contact-email', false, true, false),
        ('sparkle.userapp.auto-account.allow-login', 'true', 'boolean', 'sparkle.userapp', false, true, false),
        ('sparkle.userapp.auto-account.allow-register', 'true', 'boolean', 'sparkle.userapp', false, true, false),
        ('sparkle.userapp.auto-account.holder-uid', '1', 'number', 'sparkle.userapp', false, true, false),
        ('sparkle.version-alert.from-address', 'example@example.com', 'string', 'sparkle.version-alert', false, true, false),
        ('sparkle.version-alert.from-name', 'SparkleBTN 系统通知', 'string', 'sparkle.version-alert', false, true, false)
)
INSERT INTO public.app_config_entries
    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
SELECT config_key,
       config_value,
       value_type,
       config_group,
       CASE
           WHEN overwrite_existing THEN 'sql-default-overwrite'
           ELSE 'sql-default'
       END,
       sensitive,
       hot_reload
FROM defaults
ON CONFLICT (config_key) DO UPDATE
SET config_value = EXCLUDED.config_value,
    value_type = EXCLUDED.value_type,
    config_group = EXCLUDED.config_group,
    sensitive = EXCLUDED.sensitive,
    hot_reload = EXCLUDED.hot_reload,
    source = 'sql-default',
    updated_at = now()
WHERE EXCLUDED.source = 'sql-default-overwrite'
  AND EXCLUDED.sensitive = false
  AND app_config_entries.sensitive = false
  AND app_config_entries.config_key NOT IN (
      'sparkle.github-sync.org-name',
      'sparkle.github-sync.repository-name',
      'sparkle.version-alert.from-address',
      'sparkle.user-contact-email.from-address',
      'sparkle.userapp.auto-account.holder-uid'
  )
  AND (
      app_config_entries.config_value IS DISTINCT FROM EXCLUDED.config_value
      OR app_config_entries.value_type IS DISTINCT FROM EXCLUDED.value_type
      OR app_config_entries.config_group IS DISTINCT FROM EXCLUDED.config_group
      OR app_config_entries.sensitive IS DISTINCT FROM EXCLUDED.sensitive
      OR app_config_entries.hot_reload IS DISTINCT FROM EXCLUDED.hot_reload
      OR app_config_entries.source IS DISTINCT FROM 'sql-default'
  );

UPDATE public.app_config_entries
SET source = 'sql-default'
WHERE source = 'sql-default-overwrite';

DELETE FROM public.app_config_entries
WHERE config_key NOT LIKE 'sparkle.%';

DELETE FROM public.app_config_entries
WHERE config_key LIKE 'sparkle.machine-learning.%'
   OR config_key LIKE 'sparkle.tracker.%'
   OR config_key LIKE 'sparkle.statistics.%'
   OR config_key = 'sparkle.ranking.database-type'
   OR config_key = 'sparkle.analyse.overdownload-analyse.threshold'
   OR config_key IN (
       'sparkle.swarm-statistics-track.cron',
       'sparkle.swarm-statistics-track.duration'
   );
