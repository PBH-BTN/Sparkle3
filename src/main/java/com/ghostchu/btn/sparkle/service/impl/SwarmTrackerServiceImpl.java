package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper;
import com.ghostchu.btn.sparkle.mapper.customresult.UserSwarmStatisticTrafficResult;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.IUserappsArchivedStatisticService;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.IOException;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
@Slf4j
public class SwarmTrackerServiceImpl extends ServiceImpl<SwarmTrackerMapper, SwarmTracker> implements ISwarmTrackerService {
    @Autowired
    private ITorrentService torrentService;

    @Value("${sparkle.ping.sync-swarm.data-retention-time}")
    private long dataRetentionTime;
    @Autowired
    private IUserappsArchivedStatisticService userappsArchivedStatisticService;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;


    @Scheduled(cron = "${sparkle.ping.sync-swarm.data-retention-cron}")
    public void cronDataRetentionCleanup() {
        log.info("Performing scheduled cleanup of expired swarm tracker data...");
        OffsetDateTime threshold = OffsetDateTime.now().minus(dataRetentionTime, ChronoUnit.MILLIS);
        final int batchSize = 5000;

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(true);
        TransactionStatus status = platformTransactionManager.getTransaction(def);

        try (var cursor = baseMapper.selectExpiredSwarmTracker(threshold)) {
            long ct = 0;
            List<Long> pendingDelete = new ArrayList<>(batchSize);
            Map<Long, SwarmStatAccumulator> statsMap = new HashMap<>();

            for (SwarmTracker swarm : cursor) {
                statsMap.computeIfAbsent(swarm.getUserappsId(), k -> new SwarmStatAccumulator())
                        .accumulate(swarm);
                pendingDelete.add(swarm.getId());
                ct++;

                if (pendingDelete.size() >= batchSize) {
                    processBatch(statsMap, pendingDelete);
                    log.info("Archived {} swarm statistics so far...", ct);
                }
            }

            // Process remaining items
            if (!pendingDelete.isEmpty()) {
                processBatch(statsMap, pendingDelete);
            }

            log.info("Archived {} swarm statistics.", ct);
            platformTransactionManager.commit(status);
        } catch (Exception e) {
            if (!status.isCompleted()) {
                platformTransactionManager.rollback(status);
            }
            log.warn("Unable to cleanup expired user swarm statistics", e);
        }
    }

    private void processBatch(Map<Long, SwarmStatAccumulator> statsMap, List<Long> pendingDelete) {
        if (statsMap.isEmpty() && pendingDelete.isEmpty()) {
            return;
        }

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = platformTransactionManager.getTransaction(def);

        try {
            OffsetDateTime now = OffsetDateTime.now();
            // Update aggregated statistics
            for (Map.Entry<Long, SwarmStatAccumulator> entry : statsMap.entrySet()) {
                SwarmStatAccumulator stat = entry.getValue();
                userappsArchivedStatisticService.updateArchivedStatistic(
                        entry.getKey(),
                        stat.toPeerTraffic,
                        stat.fromPeerTraffic,
                        0,
                        stat.count,
                        now
                );
            }

            // Delete processed records
            if (!pendingDelete.isEmpty()) {
                baseMapper.deleteByIds(pendingDelete);
            }

            platformTransactionManager.commit(status);

            // Clear batch containers
            statsMap.clear();
            pendingDelete.clear();
        } catch (Exception e) {
            if (!status.isCompleted()) {
                platformTransactionManager.rollback(status);
            }
            throw new RuntimeException("Error processing batch during cleanup", e);
        }
    }

    private static class SwarmStatAccumulator {
        long toPeerTraffic = 0;
        long fromPeerTraffic = 0;
        long count = 0;

        void accumulate(SwarmTracker swarm) {
            if (swarm.getToPeerTraffic() != null) {
                this.toPeerTraffic += swarm.getToPeerTraffic();
            }
            if (swarm.getFromPeerTraffic() != null) {
                this.fromPeerTraffic += swarm.getFromPeerTraffic();
            }
            this.count++;
        }
    }


    @Transactional
    @Override
    public void syncSwarm(long userAppId, @NotNull List<BtnSwarm> swarms) {
        var nowTime = OffsetDateTime.now();
        // 使用 Map 进行内存去重，key 为 userapps_id, user_downloader, torrent_id, peer_ip, peer_port
        Map<SwarmKey, SwarmTracker> swarmMap = new HashMap<>();

        for (BtnSwarm swarm : swarms) {
            long torrentId = torrentService.getOrCreateTorrentId(
                    swarm.getTorrentIdentifier(),
                    swarm.getTorrentSize(),
                    swarm.getTorrentIsPrivate(),
                    null,
                    null
            );
            InetAddress peerIp = InetAddress.ofLiteral(swarm.getPeerIp());

            SwarmKey key = new SwarmKey(
                    userAppId,
                    swarm.getDownloader(),
                    torrentId,
                    peerIp,
                    swarm.getPort()
            );
            var lastSeenTime = swarm.getLastTimeSeen().toLocalDateTime().atOffset(ZoneOffset.UTC);
            // 相差不能超过 7 天
            if (lastSeenTime.isAfter(nowTime.plusHours(1)) || lastSeenTime.isBefore(nowTime.minusDays(7))) {
                log.debug("Ignoring swarm entry with out-of-range lastSeenTime: {}", lastSeenTime);
                continue;
            }
            SwarmTracker tracker = new SwarmTracker()
                    .setUserappsId(userAppId)
                    .setUserDownloader(swarm.getDownloader())
                    .setUserProgress(swarm.getDownloaderProgress())
                    .setTorrentId(torrentId)
                    .setPeerIp(peerIp)
                    .setPeerPort(swarm.getPort())
                    .setPeerId(swarm.getPeerId())
                    .setPeerClientName(swarm.getClientName())
                    .setPeerProgress(swarm.getPeerProgress())
                    .setFromPeerTraffic(swarm.getFromPeerTraffic())
                    .setToPeerTraffic(swarm.getToPeerTraffic())
                    .setFromPeerTrafficOffset(swarm.getFromPeerTrafficOffset())
                    .setToPeerTrafficOffset(swarm.getToPeerTrafficOffset())
                    .setFlags(swarm.getPeerLastFlags())
                    .setFirstTimeSeen(swarm.getFirstTimeSeen().toLocalDateTime().atOffset(ZoneOffset.UTC))
                    .setLastTimeSeen(lastSeenTime);

            // 如果已存在相同键，比较 lastTimeSeen，保留最新的
            swarmMap.merge(key, tracker, (existing, newTracker) ->
                    newTracker.getLastTimeSeen().isAfter(existing.getLastTimeSeen()) ? newTracker : existing
            );
        }

        if (!swarmMap.isEmpty()) {
            this.baseMapper.batchUpsert(new ArrayList<>(swarmMap.values()));
        }
    }

    /**
     * Composite key for deduplication based on userapps_id, user_downloader, torrent_id, peer_ip, peer_port
     */
    private record SwarmKey(
            long userappsId,
            String userDownloader,
            long torrentId,
            InetAddress peerIp,
            int peerPort
    ) {
    }


    @Override
    public @NotNull IPage<SwarmTracker> fetchSwarmTrackersAfter(@NotNull OffsetDateTime afterTime, @Nullable String peerIp, @Nullable Long torrentId, @NotNull Page<SwarmTracker> page) {
        QueryWrapper<SwarmTracker> wrapper = new QueryWrapper<SwarmTracker>()
                .eq(torrentId != null, "torrent_id", torrentId)
                .ge("last_time_seen", afterTime)
                .orderByDesc("last_time_seen");

        // Peer IP filter - supports both single IP and CIDR notation using <<= operator
        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= {0}::inet", peerIp.trim());
            page.setOptimizeCountSql(false); // workarond for c.b.m.e.p.i.PaginationInnerInterceptor   : optimize this sql to a count sql has exception, sql:"SELECT  id,userapps_id,user_downloader,torrent_id,peer_ip,peer_port,peer_id,peer_client_name,peer_progress,from_peer_traffic,to_peer_traffic,from_peer_traffic_offset,to_peer_traffic_offset,flags,first_time_seen,last_time_seen,user_progress  FROM swarm_tracker      WHERE  (last_time_seen >= ? AND peer_ip <<= ?::inet) ORDER BY last_time_seen DESC", exception java.util.concurrent.ExecutionException: net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "<<" "<<" at line 1, column 306. Was expecting one of: ")"
            page.setOptimizeJoinOfCountSql(false);
        }


        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTime, @NotNull String peerIp) {
        QueryWrapper<SwarmTracker> wrapper = new QueryWrapper<SwarmTracker>()
                .ge("last_time_seen", afterTime)
                .le("peer_progress", 1.0);

        // Peer IP filter - supports both single IP and CIDR notation using <<= operator
        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= {0}::inet", peerIp.trim());
        }

        return this.baseMapper.selectCount(wrapper);
    }

    @Override
    public @NotNull PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp) {
        return this.baseMapper.sumPeerIpTraffic(afterTimestamp, peerIp);
    }

    @Override
    public List<Long> selectPeerIpTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp) {
        return this.baseMapper.selectPeerTorrents(afterTimestamp, peerIp);
    }

    @Override
    public long calcPeerConcurrentSeeds(@NotNull OffsetDateTime afterTime, @NotNull String peerIp) {
        QueryWrapper<SwarmTracker> wrapper = new QueryWrapper<SwarmTracker>()
                .ge("last_time_seen", afterTime)
                .ge("peer_progress", 1.0);

        // Peer IP filter - supports both single IP and CIDR notation using <<= operator
        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= {0}::inet", peerIp.trim());
        }

        return this.baseMapper.selectCount(wrapper);
    }

//    @Scheduled(cron = "${sparkle.ping.sync-swarm.cleanup-cron}")
//    @Transactional
//    public void deleteOldData() {
//        var deleted = this.baseMapper.delete(new QueryWrapper<SwarmTracker>()
//                .le("last_time_seen", OffsetDateTime.now().minusSeconds(deleteBefore / 1000)));
//        if (deleted > 0) {
//            log.info("Deleted {} expired swarms", deleted);
//        }
//    }

    @Override
    public @NotNull IPage<SwarmTracker> querySwarmTracker(
            @Nullable Long torrentId,
            @Nullable String peerIp,
            @Nullable Integer peerPort,
            @Nullable String peerId,
            @Nullable String peerClientName,
            @Nullable Double peerProgress,
            @Nullable Long fromPeerTraffic,
            @Nullable Long toPeerTraffic,
            @Nullable String flags,
            @Nullable OffsetDateTime firstTimeSeenAfter,
            @Nullable OffsetDateTime lastTimeSeenAfter,
            @Nullable Double userProgress,
            @Nullable String sortBy,
            @Nullable String sortOrder,
            @NotNull Page<SwarmTracker> page) {

        QueryWrapper<SwarmTracker> wrapper = new QueryWrapper<>();

        // Check if there are any search conditions
        boolean hasSearchConditions = hasSearchConditions(
                torrentId, peerIp, peerPort, peerId, peerClientName,
                peerProgress, fromPeerTraffic, toPeerTraffic, flags,
                firstTimeSeenAfter, lastTimeSeenAfter, userProgress
        );

        // 添加查询条件
        wrapper.eq(torrentId != null, "torrent_id", torrentId)
                .eq(peerPort != null, "peer_port", peerPort)
                .like(peerId != null && !peerId.isBlank(), "peer_id", peerId)
                .like(peerClientName != null && !peerClientName.isBlank(), "peer_client_name", peerClientName)
                .eq(peerProgress != null, "peer_progress", peerProgress)
                .ge(fromPeerTraffic != null, "from_peer_traffic", fromPeerTraffic)
                .ge(toPeerTraffic != null, "to_peer_traffic", toPeerTraffic)
                .like(flags != null && !flags.isBlank(), "flags", flags)
                .ge(firstTimeSeenAfter != null, "first_time_seen", firstTimeSeenAfter)
                .ge(lastTimeSeenAfter != null, "last_time_seen", lastTimeSeenAfter)
                .eq(userProgress != null, "user_progress", userProgress);

        // Disable count query if there are no search conditions to improve performance
        if (!hasSearchConditions) {
            page.setSearchCount(false);
        }

        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= {0}::inet", peerIp);
            page.setOptimizeCountSql(false); // workarond for c.b.m.e.p.i.PaginationInnerInterceptor   : optimize this sql to a count sql has exception, sql:"SELECT  id,userapps_id,user_downloader,torrent_id,peer_ip,peer_port,peer_id,peer_client_name,peer_progress,from_peer_traffic,to_peer_traffic,from_peer_traffic_offset,to_peer_traffic_offset,flags,first_time_seen,last_time_seen,user_progress  FROM swarm_tracker      WHERE  (last_time_seen >= ? AND peer_ip <<= ?::inet) ORDER BY last_time_seen DESC", exception java.util.concurrent.ExecutionException: net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "<<" "<<" at line 1, column 306. Was expecting one of: ")"
            page.setOptimizeJoinOfCountSql(false);
        }


        // 添加排序
        String sort = (sortBy != null && !sortBy.isBlank()) ? sortBy : "last_time_seen";
        String order = (sortOrder != null && !sortOrder.isBlank()) ? sortOrder : "desc";
        if ("desc".equalsIgnoreCase(order)) {
            wrapper.orderByDesc(sort);
        } else {
            wrapper.orderByAsc(sort);
        }


        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public @Nullable UserSwarmStatisticTrafficResult fetchSwarmTrackerByIpInTimeRange(@NotNull String ip, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        return this.baseMapper.fetchSwarmTrackerByIpInTimeRange(ip, startAt, endAt);
    }

    @Override
    public @Nullable UserSwarmStatisticTrafficResult fetchSwarmTrackerByUserAppsInTimeRange(long userAppId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        return this.baseMapper.fetchSwarmTrackerByUserAppsInTimeRange(userAppId, startAt, endAt);
    }

    /**
     * Check if the query has any search conditions (excluding pagination and sorting)
     */
    private boolean hasSearchConditions(
            @Nullable Long torrentId,
            @Nullable String peerIp,
            @Nullable Integer peerPort,
            @Nullable String peerId,
            @Nullable String peerClientName,
            @Nullable Double peerProgress,
            @Nullable Long fromPeerTraffic,
            @Nullable Long toPeerTraffic,
            @Nullable String flags,
            @Nullable OffsetDateTime firstTimeSeenAfter,
            @Nullable OffsetDateTime lastTimeSeenAfter,
            @Nullable Double userProgress) {
        return torrentId != null
                || (peerIp != null && !peerIp.isBlank())
                || peerPort != null
                || (peerId != null && !peerId.isBlank())
                || (peerClientName != null && !peerClientName.isBlank())
                || peerProgress != null
                || fromPeerTraffic != null
                || toPeerTraffic != null
                || (flags != null && !flags.isBlank())
                || firstTimeSeenAfter != null
                || lastTimeSeenAfter != null
                || userProgress != null;
    }
}
