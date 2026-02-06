package com.ghostchu.btn.sparkle.mapper.clickhouse;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ClickHouse Mapper for Swarm Statistics Analytics
 * This mapper is used to read aggregated data from ClickHouse
 * All methods use ClickHouse-compatible SQL dialects
 */
@DS("clickhouse")
public interface SwarmStatisticsClickHouseMapper extends BaseMapper<UserSwarmStatistic> {

    /**
     * Fetch aggregated self-report statistics from ClickHouse
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
     * Fetch aggregated other-acknowledgment statistics from ClickHouse
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
