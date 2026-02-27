package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import com.ghostchu.btn.sparkle.service.dto.UniversalCountDto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public interface BanHistoryMapper extends BaseMapper<BanHistory> {

    @Nullable
    PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp);

    List<Long> selectPeerTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp);

    long estimateCountAll();

    @NotNull
    UniversalCountDto countTimeStatistics();
}
