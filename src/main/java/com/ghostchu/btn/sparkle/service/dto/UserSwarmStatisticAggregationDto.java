package com.ghostchu.btn.sparkle.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for aggregated user swarm statistics from ClickHouse
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSwarmStatisticAggregationDto implements Serializable {
    private Long userId;
    private Long sentTrafficSelfReport;
    private Long receivedTrafficSelfReport;
    private Long sentTrafficOtherAck;
    private Long receivedTrafficOtherAck;
}
