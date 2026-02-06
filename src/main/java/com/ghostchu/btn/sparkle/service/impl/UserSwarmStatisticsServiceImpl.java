package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserSwarmStatisticMapper;
import com.ghostchu.btn.sparkle.service.*;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class UserSwarmStatisticsServiceImpl extends ServiceImpl<UserSwarmStatisticMapper, UserSwarmStatistic> implements IUserSwarmStatisticsService {
    @Value("${sparkle.swarm-statistics-track.enabled}")
    private boolean enabled;
    @Value("${sparkle.swarm-statistics-track.duration}")
    private long duration;
    @Autowired
    private IUserService userService;
    @Autowired
    private IUserappService userappService;
    @Autowired
    private ISwarmTrackerService swarmTrackerService;
    @Autowired
    private IUserappsHeartbeatService heartbeatService;
    @Autowired
    private ISwarmStatisticsClickHouseService clickHouseService;
    @Autowired
    private IUserSwarmStatisticPersistenceService persistenceService;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.sent-traffic-other-ack}")
    private double rankingSentTrafficOtherAckWeight;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.received-traffic-other-ack}")
    private double rankingReceivedTrafficOtherAckWeight;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.sent-traffic-self-report}")
    private double rankingSentTrafficSelfReportWeight;
    @Value("${sparkle.ranking.weight.user-swarm-statistics.received-traffic-self-report}")
    private double rankingReceivedTrafficSelfReportWeight;

    private final ReentrantLock cronLock = new ReentrantLock();

    @Scheduled(cron = "${sparkle.swarm-statistics-track.cron}")
    public void cronUserSwarmStatisticsUpdate() {
        if (!enabled) return;
        try {
            if(!cronLock.tryLock()) return;
            OffsetDateTime startAt = OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS);
            OffsetDateTime endAt = OffsetDateTime.now();
            List<Long> uids = userService.fetchAllUserIds();
            log.info("Starting user swarm statistics update for {} users", uids.size());

            long start = System.currentTimeMillis();
            int batchSize = 15; // Process 15 users at a time to keep queries light
            int processed = 0;

            for (int i = 0; i < uids.size(); i += batchSize) {
                List<Long> batch = uids.subList(i, Math.min(i + batchSize, uids.size()));
                try {
                    // Fetch aggregated statistics from ClickHouse/PostgreSQL (read-only)
                    List<UserSwarmStatisticAggregationDto> aggregations =
                            clickHouseService.fetchAggregatedStatistics(startAt, endAt, batch);

                    // Upsert to PostgreSQL (primary datasource)
                    int updated = persistenceService.upsertStatistics(aggregations);
                    processed += updated;

                    log.info("Processed batch {}/{}: {} records updated",
                            (i / batchSize) + 1,
                            (uids.size() + batchSize - 1) / batchSize,
                            updated);
                } catch (Exception e) {
                    log.error("Error updating swarm statistics for batch starting at index {}", i, e);
                }
            }
            log.info("Processed {} user swarm statistics updates in {}ms", processed, System.currentTimeMillis() - start);
        }finally {
            cronLock.unlock();
        }
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
