package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.points.PointToggleService;
import com.ghostchu.btn.sparkle.service.points.PointsDynamicService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskKind;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.http.HttpStatus;
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
    private final PointToggleService pointToggleService;
    private final TaskStatusService taskStatusService;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, String> signatures = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    public AnalyseModuleScheduler(
            @Qualifier("taskScheduler") TaskScheduler taskScheduler,
            AnalyseModuleConfigService configService,
            AnalyseModuleExecutionService executionService,
            PointToggleService pointToggleService,
            TaskStatusService taskStatusService
    ) {
        this.taskScheduler = taskScheduler;
        this.configService = configService;
        this.executionService = executionService;
        this.pointToggleService = pointToggleService;
        this.taskStatusService = taskStatusService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @MonitoredScheduledTask(id = "analysis-schedule-startup", group = TaskGroup.STARTUP,
            nameKey = "admin.tasks.items.analysis-schedule-startup.name",
            descriptionKey = "admin.tasks.items.analysis-schedule-startup.description",
            scheduleType = TaskScheduleType.STARTUP)
    public void initialize() {
        refreshSchedules();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @MonitoredScheduledTask(
            id = "analysis-schedule-poll",
            group = TaskGroup.ANALYSIS,
            nameKey = "admin.tasks.items.analysis-schedule-poll.name",
            descriptionKey = "admin.tasks.items.analysis-schedule-poll.description",
            scheduleType = TaskScheduleType.FIXED_DELAY,
            intervalMillis = 60_000,
            initialDelayMillis = 60_000
    )
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
            if (enabled
                    && PointsDynamicService.MODULE_ID.equals(moduleId)
                    && !pointToggleService.isAnyDynamicEnabled()) {
                enabled = false;
            }
            boolean scheduleEnabled = enabled && !AnalyseModuleCron.isDisabled(cron);
            taskStatusService.configure(analysisTaskDefinition(
                    moduleId,
                    scheduleEnabled ? AnalyseModuleCron.normalize(cron) : cron,
                    scheduleEnabled
            ));
            taskStatusService.setModuleConfiguration(
                    analysisTaskId(moduleId),
                    moduleId,
                    configService.booleanValue(moduleId, "enabled", true)
            );
            taskStatusService.setManualExecutable(analysisTaskId(moduleId), true);
            if (!enabled || AnalyseModuleCron.isDisabled(cron)) {
                cancel(moduleId);
                continue;
            }
            cron = AnalyseModuleCron.normalize(cron);
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
                taskStatusService.configurationError(
                        analysisTaskId(moduleId),
                        exception.getMessage()
                );
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
        runModule(moduleId, runner, moduleRunning, "SCHEDULED");
    }

    public void triggerNow(String moduleId) throws BusinessException {
        Runnable runner = executionService.runner(moduleId);
        if (runner == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Analysis module not found");
        }
        if (!configService.isEnabled(moduleId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Analysis module is disabled");
        }
        AtomicBoolean moduleRunning = running.computeIfAbsent(
                moduleId,
                ignored -> new AtomicBoolean()
        );
        if (!moduleRunning.compareAndSet(false, true)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Analysis module is already running"
            );
        }
        Thread.startVirtualThread(() ->
                runModule(moduleId, runner, moduleRunning, "MANUAL")
        );
    }

    private void runModule(
            String moduleId,
            Runnable runner,
            AtomicBoolean moduleRunning,
            String trigger
    ) {
        TaskStatusService.TaskExecution execution = taskStatusService.begin(
                analysisTaskId(moduleId),
                trigger
        );
        long startedNanos = System.nanoTime();
        log.info("Starting analysis module {} ({})", moduleId, trigger);
        boolean executed = false;
        try {
            if (configService.isEnabled(moduleId)) {
                runner.run();
                executed = true;
            }
        } catch (Exception exception) {
            execution.failure(exception);
            log.error("Analysis module {} failed", moduleId, exception);
        } finally {
            moduleRunning.set(false);
            log.info(
                    "Finished analysis module {} ({}) in {}ms",
                    moduleId,
                    trigger,
                    java.time.Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()
            );
        }
        if (executed) {
            execution.success();
        } else {
            execution.skipped("Analysis module is disabled");
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

    private TaskDefinition analysisTaskDefinition(
            String moduleId,
            String cron,
            boolean enabled
    ) {
        return TaskDefinition.cron(
                analysisTaskId(moduleId),
                TaskGroup.ANALYSIS,
                TaskKind.SCHEDULED,
                "admin.modules.items." + moduleId + ".name",
                "admin.modules.items." + moduleId + ".description",
                cron,
                enabled
        );
    }

    private String analysisTaskId(String moduleId) {
        return "analysis-" + moduleId;
    }
}
