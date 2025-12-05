package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.entity.BanHistory;
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
public interface IBanHistoryService extends IService<BanHistory> {

    void syncBanHistory(@NotNull String submitterIp, long userAppId, @NotNull List<BtnBan> bans);

    @NotNull IPage<BanHistory> fetchBanHistory(@NotNull OffsetDateTime afterTime, @Nullable InetAddress peerIp, @Nullable Long torrentId, @Nullable List<String> moduleNames, @NotNull Page<BanHistory> page);

    @Nullable PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull Timestamp afterTimestamp, @NotNull InetAddress peerIp);

    List<Long> selectPeerTorrents(@NotNull Timestamp afterTimestamp, @NotNull InetAddress peerIp);
}
