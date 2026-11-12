package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.service.github.GithubSyncSettingsService;
import com.ghostchu.btn.sparkle.service.impl.SwarmTrackerServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.GithubSyncServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.BanHistoryServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.UserIPWarningServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.UserappsHeartbeatServiceImpl;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskKind;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import com.ghostchu.btn.sparkle.util.GlobalpingApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class BtnDynamicScheduler {
    private static final String HEARTBEAT_FLUSH_TASK = "heartbeat-queue-flush";
    private static final String HEARTBEAT_CLEANUP_TASK = "heartbeat-cleanup";
    private static final String SWARM_CLEANUP_TASK = "swarm-data-retention";
    private static final String USER_IP_WARNING_TASK = "user-ip-warning";
    private static final String GITHUB_SYNC_TASK = "github-rule-sync";
    private static final String BAN_MODULE_CACHE_TASK = "ban-module-name-cache";

    private final TaskScheduler taskScheduler;
    private final BtnRuntimeConfigService configService;
    private final UserappsHeartbeatServiceImpl heartbeatService;
    private final SwarmTrackerServiceImpl swarmTrackerService;
    private final UserIPWarningServiceImpl userIpWarningService;
    private final GithubSyncServiceImpl githubSyncService;
    private final GithubSyncSettingsService githubSyncSettingsService;
    private final BanHistoryServiceImpl banHistoryService;
    private final GlobalpingApiClient globalpingApiClient;
    private final TaskStatusService taskStatusService;

    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, String> signatures = new ConcurrentHashMap<>();

    public BtnDynamicScheduler(
            @Qualifier("taskScheduler") TaskScheduler taskScheduler,
            BtnRuntimeConfigService configService,
            UserappsHeartbeatServiceImpl heartbeatService,
            SwarmTrackerServiceImpl swarmTrackerService,
            UserIPWarningServiceImpl userIpWarningService,
            GithubSyncServiceImpl githubSyncService,
            GithubSyncSettingsService githubSyncSettingsService,
            BanHistoryServiceImpl banHistoryService,
            GlobalpingApiClient globalpingApiClient,
            TaskStatusService taskStatusService
    ) {
        this.taskScheduler = taskScheduler;
        this.configService = configService;
        this.heartbeatService = heartbeatService;
        this.swarmTrackerService = swarmTrackerService;
        this.userIpWarningService = userIpWarningService;
        this.githubSyncService = githubSyncService;
        this.githubSyncSettingsService = githubSyncSettingsService;
        this.banHistoryService = banHistoryService;
        this.globalpingApiClient = globalpingApiClient;
        this.taskStatusService = taskStatusService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        refreshSchedules();
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    @MonitoredScheduledTask(
            id = "btn-schedule-poll",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.btn-schedule-poll.name",
            descriptionKey = "admin.tasks.items.btn-schedule-poll.description",
            scheduleType = TaskScheduleType.FIXED_DELAY,
            intervalMillis = 15_000,
            initialDelayMillis = 15_000
    )
    public void pollConfigurationChanges() {
        refreshSchedules();
    }

    public synchronized void refreshSchedules() {
        globalpingApiClient.setAccessToken(
                configService.text("measure", "globalpingToken")
        );
        refreshHeartbeatFlush();
        refreshCron(
                HEARTBEAT_CLEANUP_TASK,
                "heartbeat",
                "cleanupCron",
                heartbeatService::deleteOldData,
                "admin.tasks.items.heartbeat-cleanup.name",
                "admin.tasks.items.heartbeat-cleanup.description"
        );
        refreshCron(
                SWARM_CLEANUP_TASK,
                "syncSwarm",
                "dataRetentionCron",
                swarmTrackerService::cronDataRetentionCleanup,
                "admin.tasks.items.swarm-data-retention.name",
                "admin.tasks.items.swarm-data-retention.description"
        );
        refreshCron(
                USER_IP_WARNING_TASK,
                "userIpWarning",
                "cron",
                userIpWarningService::userIpWarning,
                "admin.tasks.items.user-ip-warning.name",
                "admin.tasks.items.user-ip-warning.description"
        );
        String githubSchedule = githubSyncSettingsService.schedule();
        configureCronExpression(
                GITHUB_SYNC_TASK,
                githubSchedule,
                githubSyncService::scheduleSync,
                "admin.tasks.items.github-rule-sync.name",
                "admin.tasks.items.github-rule-sync.description"
        );
        refreshCron(
                BAN_MODULE_CACHE_TASK,
                "banHistory",
                "refreshModuleNameCacheCron",
                banHistoryService::refreshDistinctModuleNamesCache,
                "admin.tasks.items.ban-module-name-cache.name",
                "admin.tasks.items.ban-module-name-cache.description"
        );
    }

    private void refreshHeartbeatFlush() {
        long interval = Math.max(
                100,
                configService.longValue("heartbeatQueue", "flushIntervalMs")
        );
        long initialDelay = Math.max(
                0,
                configService.longValue("heartbeatQueue", "initialDelayMs")
        );
        String signature = interval + ":" + initialDelay;
        taskStatusService.configure(TaskDefinition.fixedRate(
                HEARTBEAT_FLUSH_TASK,
                TaskGroup.MAINTENANCE,
                TaskKind.SCHEDULED,
                "admin.tasks.items.heartbeat-queue-flush.name",
                "admin.tasks.items.heartbeat-queue-flush.description",
                interval,
                initialDelay,
                true
        ));
        if (signature.equals(signatures.get(HEARTBEAT_FLUSH_TASK))
                && futures.containsKey(HEARTBEAT_FLUSH_TASK)) {
            return;
        }
        cancel(HEARTBEAT_FLUSH_TASK);
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                heartbeatService::flushQueuedHeartbeats,
                Instant.now().plusMillis(initialDelay),
                Duration.ofMillis(interval)
        );
        if (future != null) {
            futures.put(HEARTBEAT_FLUSH_TASK, future);
            signatures.put(HEARTBEAT_FLUSH_TASK, signature);
            log.info(
                    "Scheduled heartbeat queue flush every {}ms after {}ms",
                    interval,
                    initialDelay
            );
        }
    }

    private void refreshCron(
            String taskId,
            String section,
            String field,
            Runnable task,
            String nameKey,
            String descriptionKey
    ) {
        configureCronExpression(
                taskId,
                configService.text(section, field).trim(),
                task,
                nameKey,
                descriptionKey
        );
    }

    private void configureCronExpression(
            String taskId,
            String expression,
            Runnable task,
            String nameKey,
            String descriptionKey
    ) {
        expression = expression == null ? "" : expression.trim();
        boolean disabled = expression.isBlank() || "-".equals(expression);
        if (disabled) {
            cancel(taskId);
            taskStatusService.configure(TaskDefinition.cron(
                    taskId,
                    TaskGroup.MAINTENANCE,
                    TaskKind.SCHEDULED,
                    nameKey,
                    descriptionKey,
                    expression.isBlank() ? "-" : expression,
                    false
            ));
            return;
        }

        try {
            CronExpression.parse(expression);
        } catch (IllegalArgumentException exception) {
            cancel(taskId);
            taskStatusService.configure(TaskDefinition.cron(
                    taskId,
                    TaskGroup.MAINTENANCE,
                    TaskKind.SCHEDULED,
                    nameKey,
                    descriptionKey,
                    expression,
                    false
            ));
            taskStatusService.configurationError(taskId, exception.getMessage());
            log.warn("Invalid cron '{}' for BTN task {}", expression, taskId);
            return;
        }

        taskStatusService.configure(TaskDefinition.cron(
                taskId,
                TaskGroup.MAINTENANCE,
                TaskKind.SCHEDULED,
                nameKey,
                descriptionKey,
                expression,
                true
        ));
        if (expression.equals(signatures.get(taskId)) && futures.containsKey(taskId)) {
            return;
        }
        cancel(taskId);
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(expression));
        if (future != null) {
            futures.put(taskId, future);
            signatures.put(taskId, expression);
            log.info("Scheduled BTN task {} with cron '{}'", taskId, expression);
        }
    }

    private void cancel(String taskId) {
        ScheduledFuture<?> future = futures.remove(taskId);
        signatures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
