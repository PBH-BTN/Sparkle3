package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class BtnAppGeoIpRiskService {
    static final String MODULE_ID = "btn-app-geoip-risk";
    private static final int SCAN_BATCH_SIZE = 200;
    private static final long CLEANUP_MILLIS = 3_600_000;
    private static final String SUBJECT = "BTN 应用归属地异常预警 / BTN app location alert";

    private final AnalyseModuleConfigService config;
    private final BtnRuntimeConfigService btn;
    private final IUserappService apps;
    private final IUserService users;
    private final UserMessageService messages;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public BtnAppGeoIpRiskService(AnalyseModuleConfigService config, BtnRuntimeConfigService btn,
                                  IUserappService apps, IUserService users, UserMessageService messages,
                                  JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.config = config;
        this.btn = btn;
        this.apps = apps;
        this.users = users;
        this.messages = messages;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public boolean isCollecting() {
        return config.isEnabled(MODULE_ID) && !config.textValue(MODULE_ID, "recipient-uids", "").isBlank();
    }

    /** Called within the heartbeat write transaction. */
    public void record(List<Receipt> receipts) {
        if (receipts.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO btn_app_geoip_observations (application_id, observed_at, address_family, country, province)
                VALUES (?, ?, ?, ?, ?)
                """, receipts, 500, (statement, receipt) -> {
            statement.setLong(1, receipt.applicationId());
            statement.setObject(2, receipt.time());
            statement.setInt(3, receipt.region().family());
            statement.setString(4, receipt.region().country());
            statement.setString(5, receipt.region().province());
        });
    }

    public void scan() {
        List<Long> configured = AnalyseModuleConfigService.recipientUids(config.textValue(MODULE_ID, "recipient-uids", ""));
        List<Long> recipients = configured.isEmpty() ? List.of() : users.listByIds(configured).stream()
                .filter(AdminPolicy::hasModeratorAccess)
                .map(User::getId)
                .toList();
        if (recipients.isEmpty()) {
            MonitoredTaskOutcome.skipped("No eligible alert recipients");
            return;
        }
        var window = Duration.ofMillis(btn.reportingWindowMillis("heartbeat"));
        int threshold = Math.max(2, config.integerValue(MODULE_ID, "geoip-region-threshold", 2));
        boolean splitFamilies = config.booleanValue(MODULE_ID, "geoip-split-ip-families", true);
        int cooldown = config.integerValue(MODULE_ID, "geoip-alert-cooldown-minutes", 60);
        int processed = 0;
        for (int batch = 0; batch < 10; batch++) {
            int size = Objects.requireNonNull(transactions.execute(status ->
                    scanBatch(window, threshold, splitFamilies, cooldown, recipients)));
            processed += size;
            if (size < SCAN_BATCH_SIZE) {
                break;
            }
        }
        if (processed == 0) {
            MonitoredTaskOutcome.skipped("No new heartbeat locations to check");
        }
    }

    private int scanBatch(Duration window, int threshold, boolean splitFamilies, int cooldown, List<Long> recipients) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended('sparkle:btn-app-geoip-risk', 0))", Boolean.class))) {
            return 0;
        }
        List<BtnGeoIpWindow.Observation> rows = jdbc.query("""
                WITH pending AS MATERIALIZED (
                    SELECT id, application_id, observed_at FROM btn_app_geoip_observations
                    WHERE NOT processed ORDER BY id LIMIT ?
                ), bounds AS (
                    SELECT application_id,
                           MIN(observed_at) - ? * INTERVAL '1 millisecond' AS start_at,
                           MAX(observed_at) + ? * INTERVAL '1 millisecond' AS end_at
                    FROM pending GROUP BY application_id
                )
                SELECT h.id, h.application_id, h.observed_at, h.address_family, h.country, h.province,
                       p.id IS NOT NULL AS pending
                FROM bounds b
                JOIN btn_app_geoip_observations h ON h.application_id = b.application_id
                     AND h.observed_at BETWEEN b.start_at AND b.end_at
                LEFT JOIN pending p ON p.id = h.id
                ORDER BY h.application_id, h.observed_at, h.id
                """, (result, index) -> new BtnGeoIpWindow.Observation(
                result.getLong(1), result.getLong(2), result.getObject(3, OffsetDateTime.class),
                result.getInt(4), result.getString(5), result.getString(6), result.getBoolean(7)),
                SCAN_BATCH_SIZE, window.toMillis(), window.toMillis());
        if (rows.isEmpty()) {
            return 0;
        }
        Map<Long, List<BtnGeoIpWindow.Hit>> hits = new LinkedHashMap<>();
        for (var application : rows.stream().collect(Collectors.groupingBy(
                BtnGeoIpWindow.Observation::applicationId, LinkedHashMap::new, Collectors.toList())).entrySet()) {
            var matches = BtnGeoIpWindow.detect(application.getValue(), window, threshold, splitFamilies);
            if (!matches.isEmpty()) {
                hits.put(application.getKey(), matches);
            }
        }
        notifyRecipients(hits, window, cooldown, recipients);
        var pending = rows.stream().filter(BtnGeoIpWindow.Observation::pending).map(BtnGeoIpWindow.Observation::id).toList();
        jdbc.batchUpdate("UPDATE btn_app_geoip_observations SET processed = true WHERE id = ?",
                pending, SCAN_BATCH_SIZE, (statement, id) -> statement.setLong(1, id));
        return pending.size();
    }

    private void notifyRecipients(Map<Long, List<BtnGeoIpWindow.Hit>> hits, Duration window,
                                  int cooldown, List<Long> recipients) {
        if (hits.isEmpty()) {
            return;
        }
        List<Userapp> applications = apps.listByIds(hits.keySet()).stream()
                .filter(app -> app.getDeleteAt() == null && !app.isSuspended())
                .toList();
        if (applications.isEmpty()) {
            return;
        }
        var owners = users.listByIds(applications.stream().map(Userapp::getOwner).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Userapp app : applications) {
            User owner = owners.get(app.getOwner());
            if (owner == null) {
                continue;
            }
            for (long recipient : recipients) {
                List<BtnGeoIpWindow.Hit> newlyAlerted = new ArrayList<>();
                for (var hit : hits.get(app.getId())) {
                    if (reserveDelivery(app.getId(), recipient, hit.family(), now, cooldown)) {
                        newlyAlerted.add(hit);
                    }
                }
                if (!newlyAlerted.isEmpty()) {
                    send(recipient, app, owner, newlyAlerted, window);
                }
            }
        }
    }

    private boolean reserveDelivery(long appId, long recipient, int family, OffsetDateTime now, int cooldown) {
        return jdbc.update("""
                INSERT INTO btn_app_geoip_deliveries (application_id, recipient_id, address_family, last_alert_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (application_id, recipient_id, address_family)
                DO UPDATE SET last_alert_at = EXCLUDED.last_alert_at
                WHERE btn_app_geoip_deliveries.last_alert_at <= ?
                """, appId, recipient, family, now, now.minusMinutes(cooldown)) == 1;
    }

    private void send(long recipient, Userapp app, User owner, List<BtnGeoIpWindow.Hit> hits, Duration window) {
        String details = hits.stream().map(hit -> {
            String scope = hit.family() == 0 ? "IPv4 + IPv6" : "IPv" + hit.family();
            return "%s · %d 个归属地 / regions · %s 至 / to %s\n%s".formatted(
                    scope, hit.regions(), hit.first(), hit.last(), String.join(", ", hit.evidence()));
        }).collect(Collectors.joining("\n"));
        String body = """
                此 BTN 应用在一个心跳上报窗口内使用了来自多个归属地的 IP。请核查是否存在跨地区共享应用的情况。
                This BTN app reported heartbeats from multiple locations within one reporting window. Please review for possible sharing across regions.

                AppId：%s
                应用主键 ID / Application record ID：%d
                用户名 / Username：%s
                用户 UID / User UID：%d
                联系邮箱 / Contact email：%s
                心跳上报窗口 / Heartbeat window：%s
                %s
                """.formatted(app.getAppId(), app.getId(), owner.getNickname(), owner.getId(),
                Objects.requireNonNullElse(owner.getEmail(), "未填写 / Unavailable"), window, details);
        try {
            if (!messages.publishSystemToUser(recipient, NotificationCategory.ACCOUNT_SECURITY, SUBJECT, body, false)) {
                throw new IllegalStateException("BTN app location alert was not delivered");
            }
        } catch (BusinessException exception) {
            throw new IllegalStateException("Unable to deliver BTN app location alert");
        }
    }

    @Scheduled(fixedDelay = CLEANUP_MILLIS, initialDelay = CLEANUP_MILLIS)
    @MonitoredScheduledTask(id = "btn-app-geoip-risk-cleanup", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.btn-app-geoip-risk-cleanup.name",
            descriptionKey = "admin.tasks.items.btn-app-geoip-risk-cleanup.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = CLEANUP_MILLIS, initialDelayMillis = CLEANUP_MILLIS)
    public void cleanup() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int removed = 0;
        for (int batch = 0; batch < 20; batch++) {
            int size = jdbc.update("""
                    WITH expired AS (
                        SELECT id FROM btn_app_geoip_observations WHERE observed_at < ?
                        ORDER BY observed_at, id LIMIT 5000 FOR UPDATE SKIP LOCKED
                    )
                    DELETE FROM btn_app_geoip_observations h USING expired e WHERE h.id = e.id
                    """, now.minusDays(3));
            removed += size;
            if (size < 5000) {
                break;
            }
        }
        removed += jdbc.update("DELETE FROM btn_app_geoip_deliveries WHERE last_alert_at < ?", now.minusDays(8));
        if (removed == 0) {
            MonitoredTaskOutcome.skipped("No expired location records");
        }
    }

    public record Receipt(long applicationId, OffsetDateTime time, Region region) {}

    public record Region(int family, String country, String province) {
        public static Region from(InetAddress address, IPGeoData geo) {
            if (geo == null || geo.getCountry() == null || geo.getCountry().getIso() == null) {
                return null;
            }
            String country = geo.getCountry().getIso().toUpperCase(Locale.ROOT);
            if (!country.matches("[A-Z]{2}")) {
                return null;
            }
            var city = geo.getCity();
            String province = city == null ? null : "CN".equals(country) ? city.getCnProvince() : city.getSubdivisionIso();
            return new Region(address instanceof Inet4Address ? 4 : 6, country,
                    province == null ? "" : province.strip());
        }
    }
}
