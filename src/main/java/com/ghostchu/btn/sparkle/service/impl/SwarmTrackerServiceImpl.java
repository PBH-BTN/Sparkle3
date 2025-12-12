package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Value("${sparkle.ping.sync-swarm.cleanup-before}")
    private long deleteBefore;

    @Transactional(propagation = Propagation.MANDATORY)
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
    ) {}


    @Override
    public @NotNull IPage<SwarmTracker> fetchSwarmTrackersAfter(@NotNull OffsetDateTime afterTime, @Nullable String peerIp, @Nullable Long torrentId, @NotNull Page<SwarmTracker> page){
        QueryWrapper<SwarmTracker> wrapper = new QueryWrapper<SwarmTracker>()
                .eq(torrentId != null, "torrent_id", torrentId)
                .ge("last_time_seen", afterTime)
                .orderByDesc("last_time_seen");

        // Peer IP filter - supports both single IP and CIDR notation using <<= operator
        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= inet {0}", peerIp.trim());
        }

        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTime, @NotNull String peerIp){
        QueryWrapper<SwarmTracker> wrapper = new QueryWrapper<SwarmTracker>()
                .ge("last_time_seen", afterTime)
                .le("peer_progress", 1.0);

        // Peer IP filter - supports both single IP and CIDR notation using <<= operator
        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= inet {0}", peerIp.trim());
        }

        return this.baseMapper.selectCount(wrapper);
    }

    @Override
    public @NotNull PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp){
        return this.baseMapper.sumPeerIpTraffic(afterTimestamp, peerIp);
    }

    @Override
    public List<Long> selectPeerIpTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp){
        return this.baseMapper.selectPeerTorrents(afterTimestamp, peerIp);
    }

    @Override
    public long calcPeerConcurrentSeeds(@NotNull OffsetDateTime afterTime, @NotNull String peerIp) {
        QueryWrapper<SwarmTracker> wrapper = new QueryWrapper<SwarmTracker>()
                .ge("last_time_seen", afterTime)
                .ge("peer_progress", 1.0);

        // Peer IP filter - supports both single IP and CIDR notation using <<= operator
        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= inet {0}", peerIp.trim());
        }

        return this.baseMapper.selectCount(wrapper);
    }

    @Scheduled(cron = "${sparkle.ping.sync-swarm.cleanup-cron}")
    @Transactional
    public void deleteOldData(){
        var deleted = this.baseMapper.delete(new QueryWrapper<SwarmTracker>()
                .le("last_time_seen", OffsetDateTime.now().minusSeconds(deleteBefore / 1000)));
        if(deleted > 0) {
            log.info("Deleted {} expired swarms", deleted);
        }
    }

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

        // 添加查询条件
        wrapper.eq(torrentId != null, "torrent_id", torrentId)
                .apply(peerIp != null && !peerIp.isBlank(), "peer_ip <<= inet {0}", peerIp)
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


}
