package com.ghostchu.btn.sparkle.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.ghostchu.btn.sparkle.mapper.customresult.UserSwarmStatisticTrafficResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @NotNull IPage<SwarmTracker> fetchSwarmTrackersAfter(@NotNull OffsetDateTime afterTime, @Nullable String peerIp, @Nullable Long torrentId, @NotNull Page<SwarmTracker> page);

    long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTime, @NotNull String peerIp);

    @Nullable PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp);

    List<Long> selectPeerIpTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp);

    long calcPeerConcurrentSeeds(@NotNull OffsetDateTime offsetDateTime, @NotNull String peerIp);

    @NotNull IPage<SwarmTracker> querySwarmTracker(
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
            @NotNull Page<SwarmTracker> page);

    @Transactional(propagation = Propagation.MANDATORY)
    @NotNull UserSwarmStatisticTrafficResult fetchSwarmTrackerByIpInTimeRange(@NotNull String ip, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);
    @Transactional(propagation = Propagation.MANDATORY)
    @NotNull UserSwarmStatisticTrafficResult fetchSwarmTrackerByUserAppsInTimeRange(long userAppId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);
}
