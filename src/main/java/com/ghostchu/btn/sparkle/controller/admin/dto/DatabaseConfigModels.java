package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class DatabaseConfigModels {
    private DatabaseConfigModels() {
    }

    public record ConfigEntry(
            String key,
            String group,
            String value,
            String valueType,
            boolean sensitive,
            boolean hotReload,
            boolean environmentOverride,
            String source,
            OffsetDateTime updatedAt,
            Long updatedBy
    ) {
    }

    public record ConfigSummary(
            long total,
            long yamlEntries,
            long databaseEntries,
            long sensitiveEntries,
            long hotReloadEntries,
            List<String> groups,
            OffsetDateTime lastUpdatedAt
    ) {
    }

    public record MigrationResult(
            int scanned,
            int inserted,
            int updated,
            int skipped,
            OffsetDateTime completedAt
    ) {
    }

    public record UpdateConfigRequest(
            String key,
            String value
    ) {
    }
}
