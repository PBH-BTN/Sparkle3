package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.SystemSample;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class AdminMetricsService {
    private static final String HISTORY_KEY = "sparkle:admin:metrics:host:history";
    private static final Duration RETENTION = Duration.ofDays(8);
    private static final long MAX_SAMPLES = RETENTION.toMinutes();
    private static final Duration KEY_TTL = RETENTION.plusDays(1);
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public AdminMetricsService(@Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    @MonitoredScheduledTask(
            id = "admin-metrics-sample",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.admin-metrics-sample.name",
            descriptionKey = "admin.tasks.items.admin-metrics-sample.description",
            scheduleType = TaskScheduleType.FIXED_DELAY,
            intervalMillis = 60_000,
            initialDelayMillis = 10_000
    )
    public void sample() {
        try {
            recordSample(currentSample());
        } catch (Exception exception) {
            log.warn("Unable to record admin host metric sample: {}", exception.getMessage());
            throw new IllegalStateException(
                    "Unable to record admin host metric sample",
                    exception
            );
        }
    }

    public @NotNull SystemSample currentSample() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        var extended = (com.sun.management.OperatingSystemMXBean) os;

        long memoryTotal = Math.max(0, extended.getTotalMemorySize());
        long memoryFree = Math.max(0, extended.getFreeMemorySize());
        long memoryUsed = Math.max(0, memoryTotal - memoryFree);
        double memoryUsage = ratio(memoryUsed, memoryTotal);

        double cpuUsage = extended.getCpuLoad();
        if (!Double.isFinite(cpuUsage) || cpuUsage < 0) {
            double loadAverage = os.getSystemLoadAverage();
            cpuUsage = loadAverage < 0 ? 0 : Math.min(1, loadAverage / Math.max(1, os.getAvailableProcessors()));
        }

        DiskUsage disk = diskUsage();
        return new SystemSample(
                Instant.now().toEpochMilli(),
                clamp(cpuUsage),
                memoryUsage,
                memoryUsed,
                memoryTotal,
                disk.usage(),
                disk.used(),
                disk.total()
        );
    }

    public @NotNull List<SystemSample> history(Duration window) {
        long now = Instant.now().toEpochMilli();
        long from = now - window.toMillis();
        Set<String> values = redisTemplate.opsForZSet().rangeByScore(HISTORY_KEY, from, now);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<SystemSample> samples = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                samples.add(objectMapper.readValue(value, SystemSample.class));
            } catch (Exception exception) {
                log.debug("Skipping malformed host metric sample: {}", exception.getMessage());
                redisTemplate.opsForZSet().remove(HISTORY_KEY, value);
            }
        }
        return downsample(samples);
    }

    private void recordSample(SystemSample sample) throws Exception {
        long now = sample.timestamp();
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        zSet.add(HISTORY_KEY, objectMapper.writeValueAsString(sample), now);
        trimHistory(now);
        redisTemplate.expire(HISTORY_KEY, KEY_TTL);
    }

    @Scheduled(cron = "0 15 * * * *")
    @MonitoredScheduledTask(
            id = "admin-metrics-cleanup",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.admin-metrics-cleanup.name",
            descriptionKey = "admin.tasks.items.admin-metrics-cleanup.description",
            expression = "0 15 * * * *"
    )
    public void cleanupHistory() {
        try {
            Long removed = trimHistory(Instant.now().toEpochMilli());
            if (removed != null && removed > 0) {
                log.info("Removed {} expired admin host metric samples", removed);
            }
        } catch (Exception exception) {
            log.warn("Unable to clean admin host metric history: {}", exception.getMessage());
            throw new IllegalStateException(
                    "Unable to clean admin host metric history",
                    exception
            );
        }
    }

    private Long trimHistory(long now) {
        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        Long expired = zSet.removeRangeByScore(HISTORY_KEY, 0, now - RETENTION.toMillis());
        Long overflow = zSet.removeRange(HISTORY_KEY, 0, -(MAX_SAMPLES + 1));
        return (expired == null ? 0 : expired) + (overflow == null ? 0 : overflow);
    }

    private List<SystemSample> downsample(List<SystemSample> samples) {
        final int maxPoints = 360;
        if (samples.size() <= maxPoints) {
            return samples;
        }
        int step = (int) Math.ceil(samples.size() / (double) maxPoints);
        List<SystemSample> result = new ArrayList<>(maxPoints + 1);
        for (int index = 0; index < samples.size(); index += step) {
            result.add(samples.get(index));
        }
        SystemSample last = samples.getLast();
        if (!result.getLast().equals(last)) {
            result.add(last);
        }
        return result;
    }

    private DiskUsage diskUsage() {
        long total = 0;
        long usable = 0;
        Set<String> seen = new HashSet<>();
        for (var root : File.listRoots()) {
            if (root == null || root.getTotalSpace() <= 0) continue;
            String key = root.getAbsolutePath().toLowerCase();
            if (!seen.add(key)) continue;
            total += root.getTotalSpace();
            usable += root.getUsableSpace();
        }

        if (total == 0) {
            File fallback = FileSystems.getDefault().getPath(".").toAbsolutePath().getRoot().toFile();
            total = fallback.getTotalSpace();
            usable = fallback.getUsableSpace();
        }
        long used = Math.max(0, total - usable);
        return new DiskUsage(used, total, ratio(used, total));
    }

    private double ratio(long used, long total) {
        return total <= 0 ? 0 : clamp(used / (double) total);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private record DiskUsage(long used, long total, double usage) {
    }
}
