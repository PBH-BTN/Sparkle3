package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface SwarmTrackerMapper extends BaseMapper<SwarmTracker> {

    void upsert(SwarmTracker swarm);

    long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTimestamp, @NotNull InetAddress peerIp);

    @NotNull
    PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull InetAddress peerIp);

    List<Long> selectPeerTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull InetAddress peerIp);

    long countAll();
}
