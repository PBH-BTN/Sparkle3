package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Service for persisting swarm statistics to the primary database
 */
public interface IUserSwarmStatisticPersistenceService {

    /**
     * Upsert aggregated statistics to the primary PostgreSQL database
     *
     * @param aggregations List of aggregated statistics
     * @return Number of records updated
     */
    int upsertStatistics(@NotNull List<UserSwarmStatisticAggregationDto> aggregations);
}
