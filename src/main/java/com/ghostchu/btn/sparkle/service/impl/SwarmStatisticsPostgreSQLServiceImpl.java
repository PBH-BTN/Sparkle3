package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.mapper.postgresql.SwarmStatisticsPostgreSQLMapper;
import com.ghostchu.btn.sparkle.service.ISwarmStatisticsClickHouseService;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import jakarta.annotation.PostConstruct;
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
 * PostgreSQL Service Implementation for Swarm Statistics
 * This implementation is used when ClickHouse is disabled
 * All methods use the primary PostgreSQL datasource
 * This is the default implementation (matchIfMissing = true)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sparkle.ranking.database-type", havingValue = "postgres", matchIfMissing = true)
public class SwarmStatisticsPostgreSQLServiceImpl implements ISwarmStatisticsClickHouseService {

    @Autowired
    private SwarmStatisticsPostgreSQLMapper postgreSQLMapper;

    @PostConstruct
    public void loaded(){
        log.info("SwarmStatisticsPostgresServiceImpl loaded");
    }


    @Override
    public @NotNull List<UserSwarmStatisticAggregationDto> fetchAggregatedStatistics(
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt,
            @NotNull List<Long> userIds) {

        log.info("Fetching aggregated statistics from PostgreSQL for {} users", userIds.size());

        // Fetch self-report statistics
        List<UserSwarmStatisticAggregationDto> selfReportStats =
                postgreSQLMapper.fetchSelfReportStats(startAt, endAt, userIds);

        // Fetch other-acknowledgment statistics
        List<UserSwarmStatisticAggregationDto> otherAckStats =
                postgreSQLMapper.fetchOtherAckStats(startAt, endAt, userIds);

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

        log.debug("Aggregated statistics for {} users from PostgreSQL", mergedMap.size());

        return new ArrayList<>(mergedMap.values());
    }
}
