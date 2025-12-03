package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
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
public class SwarmTrackerServiceImpl extends ServiceImpl<SwarmTrackerMapper, SwarmTracker> implements ISwarmTrackerService {
    @Autowired
    private ITorrentService torrentService;

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
        );
    }

    @Override
    public long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTime, @NotNull InetAddress peerIp){
        return this.baseMapper.selectCount(
                new QueryWrapper<SwarmTracker>()
                        .eq("peer_ip", peerIp)
                        .ge("last_time_seen", afterTime)
        );
    }

    @Component
    @Data
    public static class SwarmSyncBtnAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.sync-swarm.endpoint}")
        private String endpoint;
        @Value("${sparkle.ping.sync-swarm.interval}")
        private long interval;
        @Value("${sparkle.ping.sync-swarm.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;
        @Value("${sparkle.ping.sync-swarm.pow-captcha}")
        @JsonProperty("pow_captcha")
        private boolean powCaptcha;

        @Override
        public String getConfigKey() {
            return "submit_swarm";
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class SwarmTrackerDto {
        @JsonProperty("torrent")
        private String torrent;
        @JsonProperty("peer_ip")
        private InetAddress peerIp;
        @JsonProperty("peer_port")
        private Integer peerPort;
        @JsonProperty("peer_id")
        private String peerId;
        @JsonProperty("peer_client_name")
        private String peerClientName;
        @JsonProperty("peer_progress")
        private Double peerProgress;
        @JsonProperty("from_peer_traffic")
        private Long fromPeerTraffic;
        @JsonProperty("to_peer_traffic")
        private Long toPeerTraffic;
        @JsonProperty("from_peer_traffic_offset")
        private Long fromPeerTrafficOffset;
        @JsonProperty("to_peer_traffic_offset")
        private Long toPeerTrafficOffset;
        @JsonProperty("flags")
        private String flags;
        @JsonProperty(value = "first_time_seen")
        private Long firstTimeSeen;
        @JsonProperty(value = "last_time_seen")
        private Long lastTimeSeen;
        @JsonProperty("user_progress")
        private double userProgress;

        public SwarmTrackerDto(SwarmTracker swarmTracker) {
            this.torrent = "id=" + swarmTracker.getTorrentId();
            this.peerIp = swarmTracker.getPeerIp();
            this.peerPort = swarmTracker.getPeerPort();
            this.peerId = swarmTracker.getPeerId();
            this.peerClientName = swarmTracker.getPeerClientName();
            this.peerProgress = swarmTracker.getPeerProgress();
            this.fromPeerTraffic = swarmTracker.getFromPeerTraffic();
            this.toPeerTraffic = swarmTracker.getToPeerTraffic();
            this.fromPeerTrafficOffset = swarmTracker.getFromPeerTrafficOffset();
            this.toPeerTrafficOffset = swarmTracker.getToPeerTrafficOffset();
            this.flags = swarmTracker.getFlags();
            this.firstTimeSeen = swarmTracker.getFirstTimeSeen().toInstant().toEpochMilli();
            this.lastTimeSeen = swarmTracker.getLastTimeSeen().toInstant().toEpochMilli();
            this.userProgress = swarmTracker.getUserProgress();
        }
    }
}
