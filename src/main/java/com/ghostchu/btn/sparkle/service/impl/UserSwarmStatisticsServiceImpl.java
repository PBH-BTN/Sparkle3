package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserSwarmStatisticMapper;
import com.ghostchu.btn.sparkle.service.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    @Scheduled(cron = "${sparkle.swarm-statistics-track.cron}")
    public void cronUserSwarmStatisticsUpdate() {
        OffsetDateTime startAt = OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS);
        OffsetDateTime endAt = OffsetDateTime.now();
        List<Long> uids = userService.fetchAllUserIds();
        while (!uids.isEmpty()) {
            long uid = uids.removeLast();
            var result = generateUserSwarmStatistics(uid, startAt, endAt);
            baseMapper.insertOrUpdate(new UserSwarmStatistic()
                    .setUserId(uid)
                    .setSentTraffic(result.getSentTraffic().get())
                    .setReceivedTraffic(result.getReceivedTraffic().get())
                    .setIpCount(result.getIps().size())
                    .setTorrentCount(result.getTorrents().size())
                    .setLastUpdateAt(OffsetDateTime.now())
            );
        }
    }

    @NotNull
    public UserSwarmStatisticsResult generateUserSwarmStatistics(long userId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        UserSwarmStatisticsResult userSwarmStatistics = new UserSwarmStatisticsResult();
        var userApps = userappService.getUserAppsByUserId(userId);
        List<List<UserappsHeartbeat>> heartbeatBatches = new ArrayList<>();
        userApps.forEach(userApp -> heartbeatBatches.add(heartbeatService.fetchHeartBeatsByUserAppIdInTimeRange(userApp.getId(), startAt, endAt)));
        for (List<UserappsHeartbeat> heartbeats : heartbeatBatches) {
            for (UserappsHeartbeat heartbeat : heartbeats) {
                try (var cursor = swarmTrackerService.fetchSwarmTrackerByIpInTimeRange(heartbeat.getIp().getHostAddress(), startAt, endAt)) {
                    cursor.forEach(swarmTracker -> {
                        // 因为这里是从别人的视角来看的，所以应该反着来
                        userSwarmStatistics.getSentTraffic().addAndGet(swarmTracker.getFromPeerTraffic());
                        userSwarmStatistics.getReceivedTraffic().addAndGet(swarmTracker.getToPeerTraffic());
                        userSwarmStatistics.getTorrents().add(swarmTracker.getTorrentId());
                        userSwarmStatistics.getIps().add(swarmTracker.getPeerIp().getHostAddress());
                    });
                } catch (Exception ex) {
                    log.warn("Unable to fetch swarm tracker for IP {} in time range {} - {}: {}", heartbeat.getIp().getHostAddress(), startAt, endAt, ex.getMessage());
                }
            }
        }
        return userSwarmStatistics;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class UserSwarmStatisticsResult {
        private AtomicLong sentTraffic;
        private AtomicLong receivedTraffic;
        private final Set<String> ips = new HashSet<>();
        private final Set<Long> torrents = new HashSet<>();
    }
}
