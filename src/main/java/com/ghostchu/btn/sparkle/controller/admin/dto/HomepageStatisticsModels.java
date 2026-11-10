package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class HomepageStatisticsModels {
    private HomepageStatisticsModels() {
    }

    public record ModuleConfig(
            String module,
            boolean enabled,
            int refreshIntervalMinutes
    ) {
    }

    public record ModuleStatus(
            String module,
            boolean enabled,
            int refreshIntervalMinutes,
            boolean running,
            OffsetDateTime lastStartedAt,
            OffsetDateTime lastFinishedAt,
            OffsetDateTime nextRefreshAt,
            long lastDurationMs,
            String trigger,
            Long triggeredBy,
            String lastError
    ) {
    }

    public record AdminResponse(
            List<ModuleStatus> modules,
            OffsetDateTime generatedAt
    ) {
    }

    public record ConfigUpdate(
            List<ModuleConfig> modules
    ) {
    }

    public record RefreshRequest(
            String module
    ) {
    }
}
