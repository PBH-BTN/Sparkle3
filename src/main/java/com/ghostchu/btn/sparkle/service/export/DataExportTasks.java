package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.DataExportJobMapper;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataExportTasks {
    private static final long POLL_INTERVAL = 2_500;
    private static final long CLEANUP_INTERVAL = 60_000;
    private static final long HEARTBEAT_NANOS = Duration.ofSeconds(30).toNanos();
    private final SecureRandom random = new SecureRandom();
    private final DataExportService service;
    private final DataExportJobMapper jobs;
    private final ExportQueries queries;
    private final DataExportFiles files;

    @Scheduled(fixedDelay = POLL_INTERVAL)
    @MonitoredScheduledTask(id = "data-export-worker", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.data-export-worker.name",
            descriptionKey = "admin.tasks.items.data-export-worker.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = POLL_INTERVAL)
    public void generate() throws BusinessException {
        var job = service.claim();
        if (job == null) {
            MonitoredTaskOutcome.skipped("No export ready to process");
            return;
        }
        var part = files.part(job.id());
        try {
            service.requireSupporter(job.userId());
            Files.createDirectories(files.directory());
            long[] lastHeartbeat = {System.nanoTime()};
            var result = queries.write(job, part, () -> {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Export interrupted");
                long now = System.nanoTime();
                if (now - lastHeartbeat[0] >= HEARTBEAT_NANOS) {
                    if (!service.renew(job.id())) throw new IOException("Export lease expired");
                    lastHeartbeat[0] = now;
                }
            });
            if (!service.renew(job.id())) throw new IOException("Export lease expired");
            service.requireSupporter(job.userId());
            Files.move(part, files.ready(job.id()), StandardCopyOption.ATOMIC_MOVE);
            byte[] token = new byte[32];
            random.nextBytes(token);
            if (!service.complete(job, result, Base64.getUrlEncoder().withoutPadding().encodeToString(token))) {
                removeFiles(job);
                MonitoredTaskOutcome.failure("Export lease expired");
            }
        } catch (Exception exception) {
            // If publication committed before a connection error, the READY file remains owned by the job.
            log.warn("Export {} did not finish ({})", job.id(), exception.getClass().getSimpleName());
            service.fail(job, false);
            DataExportJob failed = jobs.find(job.id(), job.userId());
            if (failed != null && "FAILED".equals(failed.status())) {
                purgeFile(failed);
            }
            MonitoredTaskOutcome.failure("Unable to prepare an export");
        }
    }

    @Scheduled(fixedDelay = CLEANUP_INTERVAL)
    @MonitoredScheduledTask(id = "data-export-cleanup", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.data-export-cleanup.name",
            descriptionKey = "admin.tasks.items.data-export-cleanup.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = CLEANUP_INTERVAL)
    public void cleanup() {
        for (var job : jobs.stale()) {
            try {
                service.fail(job, true);
            } catch (Exception exception) {
                log.warn("Unable to settle interrupted export {} ({})", job.id(), exception.getClass().getSimpleName());
                MonitoredTaskOutcome.failure("Unable to settle an interrupted export");
            }
        }
        for (var job : jobs.removable()) {
            purgeFile(job);
        }
    }

    private void purgeFile(DataExportJob job) {
        try {
            removeFiles(job);
            jobs.removed(job.id());
        } catch (IOException exception) {
            log.warn("Unable to remove export files for {}", job.id());
            MonitoredTaskOutcome.failure("Unable to remove an export file");
        }
    }

    private void removeFiles(DataExportJob job) throws IOException {
        Files.deleteIfExists(files.part(job.id()));
        Files.deleteIfExists(files.ready(job.id()));
    }
}
