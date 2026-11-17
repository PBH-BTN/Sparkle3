package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.mapper.customresult.UserSwarmStatisticTrafficResult;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import com.ghostchu.btn.sparkle.service.dto.UniversalCountDto;
import org.apache.ibatis.cursor.Cursor;
import org.jetbrains.annotations.NotNull;

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
    @org.apache.ibatis.annotations.Delete("""
            WITH expired AS (
                SELECT id FROM swarm_tracker WHERE last_time_seen <= #{cutoff}
                ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED
            )
            DELETE FROM swarm_tracker s USING expired e WHERE s.id = e.id
            """)
    int deleteExpiredBatch(@Param("cutoff") java.time.OffsetDateTime cutoff, @Param("limit") int limit);

    void upsert(SwarmTracker swarm);

    void batchUpsert(List<SwarmTracker> swarms);

    long calcPeerConcurrentDownloads(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp);

    @NotNull
    PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp);

    List<Long> selectPeerTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp);

    long countAll();

    @NotNull UserSwarmStatisticTrafficResult fetchSwarmTrackerByIpInTimeRange(@NotNull String peerIp, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);

    @NotNull UserSwarmStatisticTrafficResult fetchSwarmTrackerByUserAppsInTimeRange(long userAppId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);

    @NotNull
    Cursor<SwarmTracker> selectExpiredSwarmTracker(@NotNull OffsetDateTime expiredTime);

    long estimateCountAll();

    @NotNull UniversalCountDto countTimeStatistics();
}
