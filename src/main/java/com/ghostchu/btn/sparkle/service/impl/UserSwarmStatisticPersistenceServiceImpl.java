package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.mapper.UserSwarmStatisticMapper;
import com.ghostchu.btn.sparkle.service.IUserSwarmStatisticPersistenceService;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticAggregationDto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for persisting user swarm statistics to the primary PostgreSQL database
 * This service handles transactional writes to the primary datasource
 */
@Slf4j
@Service
public class UserSwarmStatisticPersistenceServiceImpl implements IUserSwarmStatisticPersistenceService {

    @Autowired
    private UserSwarmStatisticMapper userSwarmStatisticMapper;

    @Override
    @Transactional
    public int upsertStatistics(@NotNull List<UserSwarmStatisticAggregationDto> aggregations) {
        OffsetDateTime now = OffsetDateTime.now();
        int count = 0;

        for (UserSwarmStatisticAggregationDto dto : aggregations) {
            UserSwarmStatistic statistic = new UserSwarmStatistic();
            statistic.setUserId(dto.getUserId());
            statistic.setSentTrafficSelfReport(dto.getSentTrafficSelfReport() != null ? dto.getSentTrafficSelfReport() : 0L);
            statistic.setReceivedTrafficSelfReport(dto.getReceivedTrafficSelfReport() != null ? dto.getReceivedTrafficSelfReport() : 0L);
            statistic.setSentTrafficOtherAck(dto.getSentTrafficOtherAck() != null ? dto.getSentTrafficOtherAck() : 0L);
            statistic.setReceivedTrafficOtherAck(dto.getReceivedTrafficOtherAck() != null ? dto.getReceivedTrafficOtherAck() : 0L);
            statistic.setLastUpdateAt(now);

            count += userSwarmStatisticMapper.upsertUserSwarmStatistic(statistic);
        }

        log.debug("Upserted {} user swarm statistics to primary database", count);
        return count;
    }
}
