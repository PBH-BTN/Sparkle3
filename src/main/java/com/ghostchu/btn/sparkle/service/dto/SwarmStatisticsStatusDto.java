package com.ghostchu.btn.sparkle.service.dto;

import java.time.OffsetDateTime;

public record SwarmStatisticsStatusDto(
        boolean enabled,
        boolean running,
        int refreshIntervalMinutes,
        int windowDays,
        OffsetDateTime lastStartedAt,
        OffsetDateTime lastFinishedAt,
        long lastDurationMs,
        int updatedUsers,
        String trigger,
        Long triggeredBy,
        OffsetDateTime dataUpdatedAt,
        boolean stale,
        String lastError
) {
}
