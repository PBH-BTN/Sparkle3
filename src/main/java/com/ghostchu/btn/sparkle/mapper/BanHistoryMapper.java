package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.sql.Timestamp;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface BanHistoryMapper extends BaseMapper<BanHistory> {

    @NotNull
    PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull Timestamp afterTimestamp, @NotNull InetAddress peerIp);
}
