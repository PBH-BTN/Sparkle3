package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface ISwarmTrackerService extends IService<SwarmTracker> {

    void syncSwarm(long userAppId, @NotNull List<BtnSwarm> swarms);

    @NotNull IPage<SwarmTracker> fetchSwarmTrackersAfter(@NotNull OffsetDateTime afterTime, @Nullable InetAddress peerIp, @Nullable Long torrentId, @NotNull Page<SwarmTracker> page);

    long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTime, @NotNull InetAddress peerIp);

    @Nullable PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull Timestamp afterTimestamp, @NotNull InetAddress peerIp);

    List<Long> selectPeerIpTorrents(@NotNull Timestamp afterTimestamp, @NotNull InetAddress peerIp);

    long calcPeerConcurrentSeeds(@NotNull OffsetDateTime offsetDateTime, @NotNull InetAddress peerIp);
}
