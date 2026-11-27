package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.CareerBatchMapper;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CareerDeduplicationService {
    public static final String RETENTION_DAYS_KEY = "sparkle.career.dedup-retention-days";
    private static final String CLEANUP_CRON = "0 */10 * * * *";
    private static final int BATCH_SIZE = 5000;
    private static final int MAX_BATCHES = 20;

    private final DatabaseConfigService config;
    private final CareerBatchMapper mapper;

    public static boolean validRetentionDays(String value) {
        try {
            int days = Integer.parseInt(value);
            return days >= 7 && days <= 3650;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public int retentionDays() {
        String value = config.getRawValues(List.of(RETENTION_DAYS_KEY)).getOrDefault(RETENTION_DAYS_KEY, "7");
        String override = config.getExternalOverride(RETENTION_DAYS_KEY, value);
        if (override != null) value = override;
        // Fail closed: invalid configuration must never shorten the deduplication window.
        if (!validRetentionDays(value)) {
            throw new IllegalStateException("Statistics deduplication retention must be between 7 and 3650 whole days");
        }
        return Integer.parseInt(value);
    }

    @Scheduled(cron = CLEANUP_CRON)
    @MonitoredScheduledTask(
            id = "career-deduplication-cleanup",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.career-deduplication-cleanup.name",
            descriptionKey = "admin.tasks.items.career-deduplication-cleanup.description",
            expression = CLEANUP_CRON
    )
    public void cleanup() {
        var cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays());
        int removed = 0;
        // No enclosing transaction: each bounded delete commits before the next batch.
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int events = mapper.deleteExpiredEvents(cutoff, BATCH_SIZE);
            int checkpoints = mapper.deleteExpiredCheckpoints(cutoff, BATCH_SIZE);
            removed += events + checkpoints;
            if (events < BATCH_SIZE && checkpoints < BATCH_SIZE) break;
        }
        if (removed == 0) MonitoredTaskOutcome.skipped("No expired statistics deduplication records");
    }
}
