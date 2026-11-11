package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserSwarmStatisticMapper;
import com.ghostchu.btn.sparkle.service.*;
import com.ghostchu.btn.sparkle.service.dto.SwarmStatisticsStatusDto;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskKind;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class UserSwarmStatisticsServiceImpl extends ServiceImpl<UserSwarmStatisticMapper, UserSwarmStatistic> implements IUserSwarmStatisticsService {
    private static final String REFRESH_TASK_ID = "swarm-statistics-refresh";
    @Autowired
    private IUserService userService;
    @Autowired
    private IUserappService userappService;
    @Autowired
    private ISwarmTrackerService swarmTrackerService;
    @Autowired
    private IUserappsHeartbeatService heartbeatService;
    @Autowired
    private AdminConfigService adminConfigService;
    @Autowired
    private TaskStatusService taskStatusService;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.sent-traffic-other-ack}")
    private double rankingSentTrafficOtherAckWeight;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.received-traffic-other-ack}")
    private double rankingReceivedTrafficOtherAckWeight;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.sent-traffic-self-report}")
    private double rankingSentTrafficSelfReportWeight;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.received-traffic-self-report}")
    private double rankingReceivedTrafficSelfReportWeight;

    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile OffsetDateTime lastStartedAt;
    private volatile OffsetDateTime lastFinishedAt;
    private volatile long lastDurationMs;
    private volatile int updatedUsers;
    private volatile String lastTrigger;
    private volatile Long lastTriggeredBy;
    private volatile String lastError;

    @Scheduled(cron = "0 * * * * *")
    @MonitoredScheduledTask(
            id = "swarm-statistics-scheduler",
            group = TaskGroup.STATISTICS,
            nameKey = "admin.tasks.items.swarm-statistics-scheduler.name",
            descriptionKey = "admin.tasks.items.swarm-statistics-scheduler.description",
            expression = "0 * * * * *"
    )
    public void scheduledUserSwarmStatisticsUpdate() {
        var config = adminConfigService.get();
        if (!config.swarmStatisticsEnabled()) {
            configureRefreshTask(false, config.swarmStatisticsRefreshMinutes(), null);
            return;
        }
        if (refreshing.get()) {
            return;
        }
        try {
            OffsetDateTime latest = latestCompletion();
            OffsetDateTime dueAt = latest == null
                    ? OffsetDateTime.now()
                    : latest.plusMinutes(config.swarmStatisticsRefreshMinutes());
            configureRefreshTask(true, config.swarmStatisticsRefreshMinutes(), dueAt.toInstant());
            if (OffsetDateTime.now().isBefore(dueAt)) {
                return;
            }
            startRefresh("SCHEDULED", null, config.swarmStatisticsWindowDays());
        } catch (Exception exception) {
            lastError = exception.getMessage();
            log.warn("Unable to check swarm statistics refresh schedule", exception);
            throw new IllegalStateException(
                    "Unable to check swarm statistics refresh schedule",
                    exception
            );
        }
    }

    private OffsetDateTime latestCompletion() {
        OffsetDateTime databaseUpdate = baseMapper.selectLatestUpdateAt();
        if (lastFinishedAt == null) {
            return databaseUpdate;
        }
        return databaseUpdate == null || lastFinishedAt.isAfter(databaseUpdate)
                ? lastFinishedAt
                : databaseUpdate;
    }

    @Override
    public boolean requestManualRefresh(@Nullable Long operatorId) {
        var config = adminConfigService.get();
        if (!config.swarmStatisticsEnabled()) {
            configureRefreshTask(false, config.swarmStatisticsRefreshMinutes(), null);
            return false;
        }
        configureRefreshTask(
                true,
                config.swarmStatisticsRefreshMinutes(),
                OffsetDateTime.now().toInstant()
        );
        return startRefresh(
                "MANUAL",
                operatorId,
                config.swarmStatisticsWindowDays()
        );
    }

    @Override
    public @NotNull SwarmStatisticsStatusDto getRefreshStatus() {
        var config = adminConfigService.get();
        OffsetDateTime dataUpdatedAt;
        try {
            dataUpdatedAt = latestCompletion();
        } catch (Exception exception) {
            dataUpdatedAt = lastFinishedAt;
        }
        long staleAfterMinutes = Math.max(30L, config.swarmStatisticsRefreshMinutes() * 2L);
        boolean stale = config.swarmStatisticsEnabled()
                && (dataUpdatedAt == null
                || dataUpdatedAt.plus(Duration.ofMinutes(staleAfterMinutes)).isBefore(OffsetDateTime.now()));
        return new SwarmStatisticsStatusDto(
                config.swarmStatisticsEnabled(),
                refreshing.get(),
                config.swarmStatisticsRefreshMinutes(),
                config.swarmStatisticsWindowDays(),
                lastStartedAt,
                lastFinishedAt,
                lastDurationMs,
                updatedUsers,
                lastTrigger,
                lastTriggeredBy,
                dataUpdatedAt,
                stale,
                lastError
        );
    }

    private boolean startRefresh(String trigger, @Nullable Long operatorId, int windowDays) {
        if (!refreshing.compareAndSet(false, true)) {
            return false;
        }
        OffsetDateTime endAt = OffsetDateTime.now();
        OffsetDateTime startAt = endAt.minusDays(windowDays);
        lastStartedAt = endAt;
        lastFinishedAt = null;
        lastDurationMs = 0;
        updatedUsers = 0;
        lastTrigger = trigger;
        lastTriggeredBy = operatorId;
        lastError = null;
        configureRefreshTask(
                true,
                adminConfigService.get().swarmStatisticsRefreshMinutes(),
                Instant.now()
        );
        log.info("Starting swarm statistics refresh ({}, {} day window, operator {})",
                trigger, windowDays, operatorId);
        Thread.startVirtualThread(() -> {
            TaskStatusService.TaskExecution execution = taskStatusService.begin(
                    REFRESH_TASK_ID,
                    trigger
            );
            long startedNanos = System.nanoTime();
            try {
                int updated = baseMapper.refreshUserSwarmStatistics(startAt, endAt);
                updatedUsers = updated;
                lastFinishedAt = OffsetDateTime.now();
                lastDurationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
                log.info("Refreshed {} user swarm statistics in {}ms",
                        updated, lastDurationMs);
                execution.success("Updated " + updated + " users");
            } catch (Exception exception) {
                lastFinishedAt = OffsetDateTime.now();
                lastDurationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
                lastError = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                execution.failure(exception);
                log.error("Unable to refresh user swarm statistics", exception);
            } finally {
                refreshing.set(false);
                if (lastFinishedAt != null) {
                    int refreshMinutes = adminConfigService.get().swarmStatisticsRefreshMinutes();
                    configureRefreshTask(
                            true,
                            refreshMinutes,
                            lastFinishedAt
                                    .plusMinutes(refreshMinutes)
                                    .toInstant()
                    );
                }
            }
        });
        return true;
    }

    private void configureRefreshTask(
            boolean enabled,
            int intervalMinutes,
            Instant nextRunAt
    ) {
        taskStatusService.configure(TaskDefinition.interval(
                REFRESH_TASK_ID,
                TaskGroup.STATISTICS,
                TaskKind.ASYNC,
                "admin.tasks.items.swarm-statistics-refresh.name",
                "admin.tasks.items.swarm-statistics-refresh.description",
                Duration.ofMinutes(intervalMinutes).toMillis(),
                nextRunAt,
                enabled
        ));
    }

    @Override
    public @NotNull List<UserSwarmStatisticTrackRankingDto> getUsersRanking() {
        return baseMapper.calcUsersRanking(rankingSentTrafficOtherAckWeight, rankingSentTrafficSelfReportWeight,
                rankingReceivedTrafficOtherAckWeight, rankingReceivedTrafficSelfReportWeight, userService.getSystemUids());
    }

    @Override
    public @Nullable UserSwarmStatisticTrackRankingDto getUserRanking(long userId) {
        return baseMapper.calcUserRanking(userId, rankingSentTrafficOtherAckWeight, rankingSentTrafficSelfReportWeight,
                rankingReceivedTrafficOtherAckWeight, rankingReceivedTrafficSelfReportWeight, userService.getSystemUids());
    }

    @NotNull
    public UserSwarmStatisticsResult generateUserSwarmStatistics(long userId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        UserSwarmStatisticsResult userSwarmStatistics = new UserSwarmStatisticsResult();
        var userApps = userappService.getUserAppsByUserId(userId).stream().map(Userapp::getId).toList();
        handleOtherAck(userApps, startAt, endAt, userSwarmStatistics);
        handleSelfReport(userApps, startAt, endAt, userSwarmStatistics);
        return userSwarmStatistics;
    }

    private void handleSelfReport(@NotNull List<Long> userApps, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, UserSwarmStatisticsResult userSwarmStatistics) {
        for (Long userApp : userApps) {
            var result = swarmTrackerService.fetchSwarmTrackerByUserAppsInTimeRange(userApp, startAt, endAt);
            // 以自己为视角
            if (result != null) {
                userSwarmStatistics.getSentTrafficSelfReport().addAndGet(result.getSentTraffic());
                userSwarmStatistics.getReceivedTrafficSelfReport().addAndGet(result.getReceivedTraffic());
            }
        }
    }

    private void handleOtherAck(@NotNull List<Long> userApps, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, UserSwarmStatisticsResult userSwarmStatistics) {
        List<List<UserappsHeartbeat>> heartbeatBatches = new ArrayList<>();
        userApps.forEach(userApp -> heartbeatBatches.add(heartbeatService.fetchHeartBeatsByUserAppIdInTimeRange(userApp, startAt, endAt)));
        for (List<UserappsHeartbeat> heartbeats : heartbeatBatches) {
            for (UserappsHeartbeat heartbeat : heartbeats) {
                var result = swarmTrackerService.fetchSwarmTrackerByIpInTimeRange(heartbeat.getIp().getHostAddress(), startAt, endAt);
                // 以他人为视角
                if (result != null) {
                    userSwarmStatistics.getSentTrafficOtherAck().addAndGet(result.getReceivedTraffic());
                    userSwarmStatistics.getReceivedTrafficOtherAck().addAndGet(result.getSentTraffic());
                }
            }
        }
    }

    @NoArgsConstructor
    @Data
    public static class UserSwarmStatisticsResult {
        private AtomicLong sentTrafficOtherAck = new AtomicLong();
        private AtomicLong receivedTrafficOtherAck = new AtomicLong();
        private AtomicLong sentTrafficSelfReport = new AtomicLong();
        private AtomicLong receivedTrafficSelfReport = new AtomicLong();
    }
}
