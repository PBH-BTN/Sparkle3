package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
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
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
        var swarmList = swarms.stream().map(swarm -> new SwarmTracker()
                .setUserappsId(userAppId)
                .setUserDownloader(swarm.getDownloader())
                .setUserProgress(swarm.getDownloaderProgress())
                .setTorrentId(torrentService.getOrCreateTorrentId(swarm.getTorrentIdentifier(), swarm.getTorrentSize(), swarm.getTorrentIsPrivate(), null, null))
                .setPeerIp(InetAddress.ofLiteral(swarm.getPeerIp()))
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
                .setLastTimeSeen(swarm.getLastTimeSeen().toLocalDateTime().atOffset(ZoneOffset.UTC))).toList();
        for (SwarmTracker swarmTracker : swarmList) {
            this.baseMapper.upsert(swarmTracker);
        }
    }


    @Override
    public @NotNull IPage<SwarmTracker> fetchSwarmTrackersAfter(@NotNull OffsetDateTime afterTime, @Nullable InetAddress peerIp, @Nullable Long torrentId, @NotNull Page<SwarmTracker> page){
        return this.baseMapper.selectPage(
                page,
                new QueryWrapper<SwarmTracker>()
                        .eq(torrentId != null, "torrent_id", torrentId)
                        .eq(peerIp != null, "peer_ip", peerIp)
                        .ge("last_time_seen", afterTime)
                        .orderByDesc("last_time_seen")
        );
    }

    @Override
    public long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTime, @NotNull InetAddress peerIp){
        return this.baseMapper.selectCount(
                new QueryWrapper<SwarmTracker>()
                        .eq("peer_ip", peerIp)
                        .ge("last_time_seen", afterTime)
                        .eq("peer_progress", 1.0)
        );
    }

    @Override
    public @NotNull PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull InetAddress peerIp){
        return this.baseMapper.sumPeerIpTraffic(afterTimestamp, peerIp);
    }

    @Override
    public List<Long> selectPeerIpTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull InetAddress peerIp){
        return this.baseMapper.selectPeerTorrents(afterTimestamp, peerIp);
    }

    @Override
    public long calcPeerConcurrentSeeds(@NotNull OffsetDateTime afterTime, @NotNull InetAddress peerIp) {
        return this.baseMapper.selectCount(
                new QueryWrapper<SwarmTracker>()
                        .eq("peer_ip", peerIp)
                        .ge("last_time_seen", afterTime)
                        .not(query-> query.eq("peer_progress", 1.0))
        );
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


}
