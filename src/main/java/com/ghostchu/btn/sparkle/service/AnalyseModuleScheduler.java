package com.ghostchu.btn.sparkle.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class AnalyseModuleScheduler {
    private final TaskScheduler taskScheduler;
    private final AnalyseModuleConfigService configService;
    private final AnalyseModuleExecutionService executionService;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, String> signatures = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    public AnalyseModuleScheduler(
            @Qualifier("taskScheduler") TaskScheduler taskScheduler,
            AnalyseModuleConfigService configService,
            AnalyseModuleExecutionService executionService
    ) {
        this.taskScheduler = taskScheduler;
        this.configService = configService;
        this.executionService = executionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        refreshSchedules();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void pollConfigurationChanges() {
        refreshSchedules();
    }

    public synchronized void refreshSchedules() {
        Set<String> desired = new HashSet<>();
        for (String moduleId : executionService.moduleIds()) {
            Runnable runner = executionService.runner(moduleId);
            if (runner == null) continue;
            boolean enabled = configService.isEnabled(moduleId);
            String cron = configService.textValue(moduleId, "schedule", "");
            if (!enabled || cron.isBlank()) {
                cancel(moduleId);
                continue;
            }
            String signature = cron;
            desired.add(moduleId);
            if (signature.equals(signatures.get(moduleId)) && futures.containsKey(moduleId)) {
                continue;
            }
            cancel(moduleId);
            try {
                CronTrigger trigger = new CronTrigger(cron);
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> execute(moduleId, runner),
                        trigger
                );
                if (future != null) {
                    futures.put(moduleId, future);
                    signatures.put(moduleId, signature);
                    log.info("Scheduled analysis module {} with cron '{}'", moduleId, cron);
                }
            } catch (IllegalArgumentException exception) {
                signatures.remove(moduleId);
                log.error("Invalid cron '{}' for analysis module {}", cron, moduleId, exception);
            }
        }
        Set<String> stale = new HashSet<>(futures.keySet());
        stale.removeAll(desired);
        stale.forEach(this::cancel);
    }

    private void execute(String moduleId, Runnable runner) {
        AtomicBoolean moduleRunning = running.computeIfAbsent(
                moduleId,
                ignored -> new AtomicBoolean()
        );
        if (!moduleRunning.compareAndSet(false, true)) {
            log.warn("Skipping analysis module {} because the previous run is still active", moduleId);
            return;
        }
        try {
            if (configService.isEnabled(moduleId)) {
                runner.run();
            }
        } catch (Exception exception) {
            log.error("Analysis module {} failed", moduleId, exception);
        } finally {
            moduleRunning.set(false);
        }
    }

    private void cancel(String moduleId) {
        ScheduledFuture<?> future = futures.remove(moduleId);
        signatures.remove(moduleId);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled analysis module schedule {}", moduleId);
        }
    }
}
