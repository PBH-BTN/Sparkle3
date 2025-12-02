package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
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

        @Override
        public String getConfigKey() {
            return "submit_swarm";
        }
    }
}
