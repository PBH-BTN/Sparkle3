package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserSwarmStatisticMapper;
import com.ghostchu.btn.sparkle.service.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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
    private PlatformTransactionManager transactionManager;


    @Scheduled(cron = "${sparkle.swarm-statistics-track.cron}")
    public void cronUserSwarmStatisticsUpdate() {
        if (!enabled) return;
        OffsetDateTime startAt = OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS);
        OffsetDateTime endAt = OffsetDateTime.now();
        long processed = 0;
        List<Long> uids = userService.fetchAllUserIds();
        long total = uids.size();
        while (!uids.isEmpty()) {
            long uid = uids.removeLast();
            var result = generateUserSwarmStatistics(uid, startAt, endAt);
            baseMapper.insertOrUpdate(new UserSwarmStatistic()
                    .setUserId(uid)
                    .setSentTrafficOtherAck(result.getSentTrafficOtherAck().get())
                    .setReceivedTrafficOtherAck(result.getReceivedTrafficOtherAck().get())
                    .setSentTrafficSelfReport(result.getSentTrafficSelfReport().get())
                    .setReceivedTrafficSelfReport(result.getReceivedTrafficSelfReport().get())
                    .setIpCount(result.getIps().size())
                    .setTorrentCount(result.getTorrents().size())
                    .setLastUpdateAt(OffsetDateTime.now())
            );
            processed++;
        }
        log.info("Processed {} user swarm statistics updates", processed);
    }

    @NotNull
    public UserSwarmStatisticsResult generateUserSwarmStatistics(long userId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        UserSwarmStatisticsResult userSwarmStatistics = new UserSwarmStatisticsResult();
        var userApps = userappService.getUserAppsByUserId(userId);
        handleOtherAck(userApps, startAt, endAt, userSwarmStatistics);
        handleSelfReport(userApps, startAt, endAt, userSwarmStatistics);
        return userSwarmStatistics;
    }

    private void handleSelfReport(@NotNull List<Userapp> userApps, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, UserSwarmStatisticsResult userSwarmStatistics) {
        for (Userapp userApp : userApps) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult((_) -> {
                try (var cursor = swarmTrackerService.fetchSwarmTrackerByUserAppsInTimeRange(userApp.getId(), startAt, endAt)) {
                    cursor.forEach(swarmTracker -> {
                        // 因为这里是从自己的视角来看的，所以应该正着来
                        userSwarmStatistics.getReceivedTrafficSelfReport().addAndGet(swarmTracker.getFromPeerTraffic());
                        userSwarmStatistics.getSentTrafficSelfReport().addAndGet(swarmTracker.getToPeerTraffic());
                        userSwarmStatistics.getTorrents().add(swarmTracker.getTorrentId());
                    });
                } catch (Exception ex) {
                    log.warn("Unable to fetch swarm tracker for userAppId {} in time range {} - {}: {}", userApp.getId(), startAt, endAt, ex.getMessage());
                }
            });
        }
    }

    private void handleOtherAck(@NotNull List<Userapp> userApps, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, UserSwarmStatisticsResult userSwarmStatistics) {
        List<List<UserappsHeartbeat>> heartbeatBatches = new ArrayList<>();
        userApps.forEach(userApp -> heartbeatBatches.add(heartbeatService.fetchHeartBeatsByUserAppIdInTimeRange(userApp.getId(), startAt, endAt)));
        for (List<UserappsHeartbeat> heartbeats : heartbeatBatches) {
            for (UserappsHeartbeat heartbeat : heartbeats) {
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult((_) -> {
                    try (var cursor = swarmTrackerService.fetchSwarmTrackerByIpInTimeRange(heartbeat.getIp().getHostAddress(), startAt, endAt)) {
                        cursor.forEach(swarmTracker -> {
                            // 因为这里是从别人的视角来看的，所以应该反着来
                            userSwarmStatistics.getSentTrafficOtherAck().addAndGet(swarmTracker.getFromPeerTraffic());
                            userSwarmStatistics.getReceivedTrafficOtherAck().addAndGet(swarmTracker.getToPeerTraffic());
                            userSwarmStatistics.getTorrents().add(swarmTracker.getTorrentId());
                            userSwarmStatistics.getIps().add(swarmTracker.getPeerIp().getHostAddress());
                        });
                    } catch (Exception ex) {
                        log.warn("Unable to fetch swarm tracker for IP {} in time range {} - {}: {}", heartbeat.getIp().getHostAddress(), startAt, endAt, ex.getMessage());
                    }
                });
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
        private final Set<String> ips = new HashSet<>();
        private final Set<Long> torrents = new HashSet<>();
    }
}
