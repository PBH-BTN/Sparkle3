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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BtnReportVolumeService {
    private static final int QUEUE_LIMIT = 100_000;
    private static final int DELIVERY_DELAY = 5_000;
    private static final int DELIVERY_BATCH_SIZE = 100;
    private static final DefaultRedisScript<Long> COUNT_REPORT = counterScript();
    private static final DefaultRedisScript<Long> TRIM_DELIVERED = new DefaultRedisScript<>(
            "return redis.call('XTRIM', KEYS[1], 'MINID', ARGV[1]) + redis.call('XDEL', KEYS[1], ARGV[1])",
            Long.class);

    private final BtnRuntimeConfigService config;
    private final RedisTemplate<String, String> redis;
    private final IUserappService apps;
    private final IUserService users;
    private final UserMessageService messages;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AtomicLong nextWarning = new AtomicLong();

    public BtnReportVolumeService(BtnRuntimeConfigService config,
                                  @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redis,
                                  IUserappService apps, IUserService users, UserMessageService messages,
                                  JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.config = config;
        this.redis = redis;
        this.apps = apps;
        this.users = users;
        this.messages = messages;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    private static DefaultRedisScript<Long> counterScript() {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("redis/btn-report-volume.lua"));
        script.setResultType(Long.class);
        return script;
    }

    public void record(Module module, long appId, int rows) {
        if (rows <= 0 || !config.booleanValue(module.section, "volumeRiskEnabled")
                || config.text(module.section, "volumeRiskRecipientUids").isBlank()) {
            return;
        }
        long window = config.reportingWindowMillis(module.section);
        long threshold = config.longValue(module.section, "volumeRiskThreshold");
        String appKey = module.prefix() + ":" + appId + ":" + window + ":" + threshold;
        try {
            Long result = redis.execute(COUNT_REPORT, List.of(appKey + ":time", appKey + ":total",
                            appKey + ":cooldown", module.stream()), Long.toString(window), Long.toString(threshold),
                    Integer.toString(rows), Long.toString(appId), Integer.toString(QUEUE_LIMIT));
            if (Objects.equals(result, -1L)) {
                warn("pending alert queue is full");
            }
        } catch (RuntimeException exception) {
            warn(exception.getClass().getSimpleName());
        }
    }

    private void warn(String reason) {
        long now = System.currentTimeMillis();
        long next = nextWarning.get();
        if (now >= next && nextWarning.compareAndSet(next, now + 60_000)) {
            log.warn("Unable to queue a BTN report volume alert ({})", reason);
        }
    }

    @Scheduled(fixedDelay = DELIVERY_DELAY, initialDelay = DELIVERY_DELAY)
    @MonitoredScheduledTask(id = "btn-report-volume-alerts", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.btn-report-volume-alerts.name",
            descriptionKey = "admin.tasks.items.btn-report-volume-alerts.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = DELIVERY_DELAY, initialDelayMillis = DELIVERY_DELAY)
    public void deliver() {
        boolean handled = false;
        for (Module module : Module.values()) {
            try {
                // Avoid database work while idle or when the feature has never been enabled.
                Long queued = redis.opsForStream().size(module.stream());
                if (queued == null || queued == 0) {
                    continue;
                }
                String cursor = transactions.execute(status -> deliverBatch(module));
                if (cursor != null) {
                    handled = true;
                    redis.execute(TRIM_DELIVERED, List.of(module.stream()), cursor);
                }
            } catch (RuntimeException exception) {
                MonitoredTaskOutcome.failure("Unable to deliver BTN report volume alerts");
                log.warn("BTN report volume delivery failed for {} ({})", module.section,
                        exception.getClass().getSimpleName());
            }
        }
        if (!handled) {
            MonitoredTaskOutcome.skipped("No pending BTN report volume alerts");
        }
    }

    private String deliverBatch(Module module) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))",
                Boolean.class, module.stream()))) {
            return null;
        }
        String cursor = jdbc.queryForList("SELECT stream_id FROM btn_report_volume_cursors WHERE module = ?",
                String.class, module.section).stream().findFirst().orElse("0-0");
        var records = redis.<String, String>opsForStream().range(module.stream(),
                Range.rightUnbounded(Range.Bound.exclusive(cursor)), Limit.limit().count(DELIVERY_BATCH_SIZE));
        if (records == null || records.isEmpty()) {
            return cursor;
        }
        var recipients = recipients(module);
        if (!recipients.isEmpty()) {
            var alerts = records.stream().map(record -> Alert.from(record.getValue())).toList();
            var applications = apps.listByIds(alerts.stream().map(Alert::appId).distinct().toList()).stream()
                    .filter(app -> app.getDeleteAt() == null && !app.isSuspended())
                    .collect(Collectors.toMap(Userapp::getId, app -> app));
            for (Alert alert : alerts) {
                Userapp app = applications.get(alert.appId());
                if (app != null) {
                    for (long recipient : recipients) {
                        send(module, app, alert, recipient);
                    }
                }
            }
        }
        String through = records.getLast().getId().getValue();
        jdbc.update("""
                INSERT INTO btn_report_volume_cursors (module, stream_id) VALUES (?, ?)
                ON CONFLICT (module) DO UPDATE SET stream_id = EXCLUDED.stream_id
                """, module.section, through);
        return through;
    }

    private List<Long> recipients(Module module) {
        if (!config.booleanValue(module.section, "volumeRiskEnabled")) {
            return List.of();
        }
        var configured = AnalyseModuleConfigService.recipientUids(config.text(module.section, "volumeRiskRecipientUids"));
        return configured.isEmpty() ? List.of() : users.listByIds(configured).stream()
                .filter(AdminPolicy::hasModeratorAccess).map(User::getId).toList();
    }

    private void send(Module module, Userapp app, Alert alert, long recipient) {
        String content = """
                此 BTN 应用在一个汇报窗口内的条目数超过设定阈值。请核查上报频率和数据量。
                This BTN application exceeded the configured entry count within one reporting window. Review its reporting frequency and volume.

                上报模块 / Reporting module：%s
                AppId：%s
                应用主键 ID / Application record ID：%d
                用户 UID / User UID：%d
                窗口内条目数（至少）/ Entries within window (at least)：%d
                阈值 / Threshold：%d
                汇报窗口 / Reporting window：%s
                发现时间 / Detected at：%s
                """.formatted(module.label, app.getAppId(), app.getId(), app.getOwner(), alert.count(),
                alert.threshold(), Duration.ofMillis(alert.window()), Instant.ofEpochMilli(alert.observedAt()));
        try {
            if (!messages.publishSystemToUser(recipient, NotificationCategory.ACCOUNT_SECURITY,
                    "BTN 应用上报量预警 / BTN reporting volume alert", content, false)) {
                throw new IllegalStateException("BTN report volume alert was not delivered");
            }
        } catch (BusinessException exception) {
            throw new IllegalStateException("Unable to deliver BTN report volume alert");
        }
    }

    public enum Module {
        HEARTBEAT("heartbeat", "心跳 / Heartbeat"),
        BAN_HISTORY("syncBanHistory", "封禁历史 / Ban History"),
        SWARM("syncSwarm", "种群数据 / Swarm");

        private final String section;
        private final String label;

        Module(String section, String label) {
            this.section = section;
            this.label = label;
        }

        private String prefix() {
            return "sparkle:{btn-report-volume:" + section + "}";
        }

        String stream() {
            return prefix() + ":alerts";
        }
    }

    private record Alert(long appId, long count, long threshold, long window, long observedAt) {
        static Alert from(Map<String, String> fields) {
            return new Alert(Long.parseLong(fields.get("application")), Long.parseLong(fields.get("count")),
                    Long.parseLong(fields.get("threshold")), Long.parseLong(fields.get("window")),
                    Long.parseLong(fields.get("observed_at")));
        }
    }
}
