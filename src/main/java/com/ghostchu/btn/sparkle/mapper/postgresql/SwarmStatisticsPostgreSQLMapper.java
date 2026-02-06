package com.ghostchu.btn.sparkle.mapper.postgresql;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * PostgreSQL Mapper for Swarm Statistics Analytics
 * This mapper uses PostgreSQL-specific SQL dialects for reading aggregated data
 */
public interface SwarmStatisticsPostgreSQLMapper extends BaseMapper<UserSwarmStatistic> {

    /**
     * Fetch aggregated self-report statistics from PostgreSQL
     * @param startAt Start timestamp
     * @param endAt End timestamp
     * @param userIds List of user IDs to process
     * @return List of aggregated statistics per user
     */
    @NotNull
    List<UserSwarmStatisticAggregationDto> fetchSelfReportStats(
            @Param("startAt") @NotNull OffsetDateTime startAt,
            @Param("endAt") @NotNull OffsetDateTime endAt,
            @Param("userIds") @NotNull List<Long> userIds
    );

    /**
     * Fetch aggregated other-acknowledgment statistics from PostgreSQL
     * @param startAt Start timestamp
     * @param endAt End timestamp
     * @param userIds List of user IDs to process
     * @return List of aggregated statistics per user
     */
    @NotNull
    List<UserSwarmStatisticAggregationDto> fetchOtherAckStats(
            @Param("startAt") @NotNull OffsetDateTime startAt,
            @Param("endAt") @NotNull OffsetDateTime endAt,
            @Param("userIds") @NotNull List<Long> userIds
    );
}
