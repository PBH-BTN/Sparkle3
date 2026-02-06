package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for querying swarm statistics from ClickHouse
 * All methods in this service read from ClickHouse (read-only datasource)
 */
public interface ISwarmStatisticsAggrService {

    /**
     * Fetch aggregated swarm statistics for given users from ClickHouse
     * Combines both self-report and other-acknowledgment statistics
     *
     * @param startAt Start timestamp
     * @param endAt End timestamp
     * @param userIds List of user IDs to process
     * @return List of aggregated statistics per user
     */
    @NotNull
    List<UserSwarmStatisticAggregationDto> fetchAggregatedStatistics(
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt,
            @NotNull List<Long> userIds
    );
}
