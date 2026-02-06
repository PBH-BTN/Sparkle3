package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.ghostchu.btn.sparkle.mapper.clickhouse.SwarmStatisticsClickHouseMapper;
import com.ghostchu.btn.sparkle.service.ISwarmStatisticsClickHouseService;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse Service Implementation for Swarm Statistics
 * All methods use the ClickHouse datasource for read-only analytics queries
 * This implementation is active when sparkle.ranking.use-clickhouse=true
 */
@Slf4j
@Service
@DS("clickhouse")
@ConditionalOnProperty(name = "sparkle.ranking.use-clickhouse", havingValue = "true")
public class SwarmStatisticsClickHouseServiceImpl implements ISwarmStatisticsClickHouseService {

    @Autowired
    private SwarmStatisticsClickHouseMapper clickHouseMapper;

    @Override
    public @NotNull List<UserSwarmStatisticAggregationDto> fetchAggregatedStatistics(
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt,
            @NotNull List<Long> userIds) {

        log.debug("Fetching aggregated statistics from ClickHouse for {} users", userIds.size());

        // Fetch self-report statistics
        List<UserSwarmStatisticAggregationDto> selfReportStats =
                clickHouseMapper.fetchSelfReportStats(startAt, endAt, userIds);

        // Fetch other-acknowledgment statistics
        List<UserSwarmStatisticAggregationDto> otherAckStats =
                clickHouseMapper.fetchOtherAckStats(startAt, endAt, userIds);

        // Merge the results by user ID
        Map<Long, UserSwarmStatisticAggregationDto> mergedMap = new HashMap<>();

        // Add self-report stats
        for (UserSwarmStatisticAggregationDto dto : selfReportStats) {
            mergedMap.put(dto.getUserId(), dto);
        }

        // Merge with other-ack stats
        for (UserSwarmStatisticAggregationDto dto : otherAckStats) {
            if (mergedMap.containsKey(dto.getUserId())) {
                UserSwarmStatisticAggregationDto existing = mergedMap.get(dto.getUserId());
                existing.setSentTrafficOtherAck(dto.getSentTrafficOtherAck());
                existing.setReceivedTrafficOtherAck(dto.getReceivedTrafficOtherAck());
            } else {
                mergedMap.put(dto.getUserId(), dto);
            }
        }

        // Fill in missing users with zero values
        for (Long userId : userIds) {
            if (!mergedMap.containsKey(userId)) {
                mergedMap.put(userId, new UserSwarmStatisticAggregationDto(
                        userId, 0L, 0L, 0L, 0L
                ));
            }
        }

        log.debug("Aggregated statistics for {} users from ClickHouse", mergedMap.size());

        return new ArrayList<>(mergedMap.values());
    }
}
