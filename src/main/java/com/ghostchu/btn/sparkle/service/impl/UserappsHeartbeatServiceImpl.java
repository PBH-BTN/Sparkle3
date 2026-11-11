package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.points.PointAwardBucket;
import com.ghostchu.btn.sparkle.service.points.PointRulesService;
import com.ghostchu.btn.sparkle.service.points.PointToggleService;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batches Keepalive requests in Redis before writing heartbeat rows and their
 * point awards to PostgreSQL.
 */
@Service
@Slf4j
public class UserappsHeartbeatServiceImpl
        extends ServiceImpl<UserAppsHeartbeatMapper, UserappsHeartbeat>
        implements IUserappsHeartbeatService {
    private static final String QUEUE_KEY = "sparkle:ping:heartbeat:queue";
    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('RPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], -tonumber(ARGV[2]), -1)
            return 1
            """,
            Long.class
    );
    private static final DefaultRedisScript<List> DRAIN_SCRIPT = new DefaultRedisScript<>(
            """
            local result = {}
            for i = 1, tonumber(ARGV[1]) do
                local value = redis.call('LPOP', KEYS[1])
                if not value then
                    break
                end
                table.insert(result, value)
            end
            return result
            """,
            List.class
    );

    @Value("${sparkle.ping.heartbeat.cleanup-before}")
    private long deleteBefore;
    @Value("${sparkle.ping.heartbeat.queue.batch-size:2000}")
    private int batchSize;
    @Value("${sparkle.ping.heartbeat.queue.max-size:100000}")
    private int maxQueueSize;

    @Autowired
    private IPDBManager geoIPManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private PointsService pointsService;
    @Autowired
    private PointRulesService pointRulesService;
    @Autowired
    private PointToggleService pointToggleService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final AtomicLong seq = new AtomicLong();
    private final AtomicBoolean flushing = new AtomicBoolean();

    @Override
    public @NotNull List<UserappsHeartbeat> fetchHeartBeatsByUserAppIdInTimeRange(
            long userAppId,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt
    ) {
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .eq("userapp_id", userAppId)
                .ge("last_seen_at", startAt)
                .le("first_seen_at", endAt)
                .orderByDesc("last_seen_at")
        );
    }

    @Override
    public @NotNull List<UserappsHeartbeat> fetchIpHeartbeatRecords(
            @NotNull String peerIp,
            @NotNull OffsetDateTime after
    ) {
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .apply("ip <<= {0}::inet", peerIp)
                .gt("last_seen_at", after)
                .orderByDesc("last_seen_at")
        );
    }

    @Override
    public void onHeartBeat(long userAppId, long userId, @NotNull InetAddress ip) {
        HeartbeatEvent event = new HeartbeatEvent(
                userAppId,
                userId,
                ip.getHostAddress(),
                OffsetDateTime.now(),
                pointToggleService.isEnabled(PointCategory.KEEPALIVE)
                        ? pointRulesService.points(PointCategory.KEEPALIVE)
                        : 0,
                pointToggleService.isEnabled(PointCategory.ACTIVE_APP)
                        ? pointRulesService.points(PointCategory.ACTIVE_APP)
                        : 0,
                pointToggleService.isEnabled(PointCategory.APP_ONLINE)
        );
        try {
            enqueue(event);
        } catch (Exception exception) {
            log.warn("Unable to enqueue Keepalive for user application {}, writing directly",
                    userAppId, exception);
            persistEvents(List.of(event));
        }
    }

    @Scheduled(
            fixedDelayString = "${sparkle.ping.heartbeat.queue.flush-interval-ms:1000}",
            initialDelayString = "${sparkle.ping.heartbeat.queue.initial-delay-ms:1000}"
    )
    @MonitoredScheduledTask(
            id = "heartbeat-queue-flush",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.heartbeat-queue-flush.name",
            descriptionKey = "admin.tasks.items.heartbeat-queue-flush.description",
            scheduleType = TaskScheduleType.FIXED_DELAY,
            intervalMillis = 1_000,
            initialDelayMillis = 1_000
    )
    public void flushQueuedHeartbeats() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            List<String> rawEvents = drain();
            if (rawEvents.isEmpty()) {
                return;
            }
            List<HeartbeatEvent> events = new ArrayList<>(rawEvents.size());
            for (String rawEvent : rawEvents) {
                try {
                    events.add(objectMapper.readValue(rawEvent, HeartbeatEvent.class));
                } catch (Exception exception) {
                    log.warn("Discarding malformed queued Keepalive event", exception);
                }
            }
            if (events.isEmpty()) {
                return;
            }
            try {
                persistEvents(events);
                log.debug("Flushed {} Keepalive events", events.size());
            } catch (Exception exception) {
                requeue(rawEvents);
                log.error("Unable to flush queued Keepalive events; requeued {}",
                        rawEvents.size(), exception);
            }
        } catch (Exception exception) {
            log.error("Unable to drain Keepalive queue", exception);
        } finally {
            flushing.set(false);
        }
    }

    private void enqueue(@NotNull HeartbeatEvent event) throws Exception {
        redisTemplate.execute(
                ENQUEUE_SCRIPT,
                List.of(QUEUE_KEY),
                objectMapper.writeValueAsString(event),
                Integer.toString(Math.max(1, maxQueueSize))
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> drain() {
        Object result = redisTemplate.execute(
                DRAIN_SCRIPT,
                List.of(QUEUE_KEY),
                Integer.toString(Math.max(1, batchSize))
        );
        if (!(result instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        List<String> events = new ArrayList<>(values.size());
        for (Object value : values) {
            events.add(value instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : value.toString());
        }
        return events;
    }

    private void requeue(List<String> rawEvents) {
        try {
            redisTemplate.opsForList().rightPushAll(QUEUE_KEY, rawEvents);
        } catch (Exception exception) {
            log.error("Unable to requeue {} Keepalive events", rawEvents.size(), exception);
        }
    }

    private void persistEvents(List<HeartbeatEvent> events) {
        Map<HeartbeatKey, HeartbeatAccumulator> deduplicated = new LinkedHashMap<>();
        for (HeartbeatEvent event : events) {
            HeartbeatKey key = new HeartbeatKey(event.userappId(), event.ip());
            deduplicated.computeIfAbsent(
                    key,
                    ignored -> new HeartbeatAccumulator(event)
            ).merge(event);
        }
        List<UserappsHeartbeat> rows = new ArrayList<>(deduplicated.size());
        for (HeartbeatAccumulator heartbeat : deduplicated.values()) {
            InetAddress address = InetAddress.ofLiteral(heartbeat.ip);
            rows.add(new UserappsHeartbeat()
                    .setId(Hashing.sha256().hashString(
                            heartbeat.userappId
                                    + heartbeat.ip
                                    + UUID.randomUUID()
                                    + seq.incrementAndGet(),
                            StandardCharsets.UTF_8
                    ).asLong())
                    .setUserappId(heartbeat.userappId)
                    .setIp(address)
                    .setGeoip(geoIPManager.queryIPDB(address).geoData().get())
                    .setFirstSeenAt(heartbeat.firstSeenAt)
                    .setLastSeenAt(heartbeat.lastSeenAt));
        }
        List<PointAwardBucket> pointBuckets = aggregatePoints(events);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (!rows.isEmpty()) {
                baseMapper.batchUpsert(rows);
            }
            if (!pointBuckets.isEmpty()) {
                pointsService.commitAll(pointBuckets);
            }
        });
    }

    private List<PointAwardBucket> aggregatePoints(List<HeartbeatEvent> events) {
        Map<Long, List<HeartbeatEvent>> byUser = new LinkedHashMap<>();
        for (HeartbeatEvent event : events) {
            byUser.computeIfAbsent(event.userId(), ignored -> new ArrayList<>())
                    .add(event);
        }
        String pointDay = pointsService.currentPointDay().toString();
        List<PointAwardBucket> buckets = new ArrayList<>(byUser.size());
        for (Map.Entry<Long, List<HeartbeatEvent>> entry : byUser.entrySet()) {
            PointAwardBucket bucket = new PointAwardBucket(
                    entry.getKey(),
                    "KEEPALIVE_BATCH",
                    OffsetDateTime.now()
            );
            long keepalivePoints = 0;
            for (HeartbeatEvent event : entry.getValue()) {
                if (event.activeAppPoints() > 0) {
                    bucket.addClaim(
                            "ACTIVE_APP_DAILY",
                            pointDay + ":" + event.userappId(),
                            PointCategory.ACTIVE_APP,
                            event.activeAppPoints()
                    );
                }
                if (event.trackOnline()) {
                    bucket.addAppActivity(event.userappId());
                }
                keepalivePoints = safeAdd(keepalivePoints, event.keepalivePoints());
            }
            if (keepalivePoints > 0) {
                bucket.addContribution(
                        PointCategory.KEEPALIVE,
                        keepalivePoints,
                        entry.getValue().size(),
                        entry.getValue().size()
                );
            }
            if (bucket.hasWork()) {
                buckets.add(bucket);
            }
        }
        return buckets;
    }

    private long safeAdd(long first, long second) {
        if (second <= 0) {
            return first;
        }
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    @Scheduled(cron = "${sparkle.ping.heartbeat.cleanup-cron}")
    @MonitoredScheduledTask(
            id = "heartbeat-cleanup",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.heartbeat-cleanup.name",
            descriptionKey = "admin.tasks.items.heartbeat-cleanup.description",
            expression = "${sparkle.ping.heartbeat.cleanup-cron}"
    )
    @org.springframework.transaction.annotation.Transactional
    public void deleteOldData() {
        long deleted = 0;
        int lastDelete;
        OffsetDateTime cutoffTime = OffsetDateTime.now()
                .minus(deleteBefore, ChronoUnit.MILLIS);
        do {
            List<Long> idsToDelete = this.baseMapper.selectList(
                            new LambdaQueryWrapper<UserappsHeartbeat>()
                                    .select(UserappsHeartbeat::getId)
                                    .le(UserappsHeartbeat::getLastSeenAt, cutoffTime)
                                    .last("LIMIT 1000")
                    )
                    .stream()
                    .map(UserappsHeartbeat::getId)
                    .toList();

            if (idsToDelete.isEmpty()) {
                lastDelete = 0;
            } else {
                lastDelete = this.baseMapper.deleteByIds(idsToDelete);
                deleted += lastDelete;
                if (deleted % 10000 == 0) {
                    log.info("Deleted {} old heartbeat records so far...", deleted);
                }
            }
        } while (lastDelete > 0);
        log.info("Deleted {} old heartbeat records", deleted);
    }

    private record HeartbeatEvent(
            long userappId,
            long userId,
            String ip,
            OffsetDateTime observedAt,
            long keepalivePoints,
            long activeAppPoints,
            boolean trackOnline
    ) {
    }

    private record HeartbeatKey(long userappId, String ip) {
    }

    private static final class HeartbeatAccumulator {
        private final long userappId;
        private final String ip;
        private OffsetDateTime firstSeenAt;
        private OffsetDateTime lastSeenAt;

        private HeartbeatAccumulator(HeartbeatEvent event) {
            this.userappId = event.userappId();
            this.ip = event.ip();
            this.firstSeenAt = event.observedAt();
            this.lastSeenAt = event.observedAt();
        }

        private void merge(HeartbeatEvent event) {
            if (event.observedAt().isBefore(firstSeenAt)) {
                firstSeenAt = event.observedAt();
            }
            if (event.observedAt().isAfter(lastSeenAt)) {
                lastSeenAt = event.observedAt();
            }
        }
    }
}
