package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class PointsModels {
    private PointsModels() {
    }

    public record CategoryPoints(
            String category,
            String pointsRaw,
            String dailyCapRaw,
            boolean enabled
    ) {
    }

    public record FixedPointsSummary(
            String pointsTodayRaw,
            String cappedPointsTodayRaw,
            String dailyCapRaw,
            long averagePerHourRaw,
            List<CategoryPoints> categories
    ) {
    }

    public record OneTimePointsSummary(
            String pointsTodayRaw,
            long averagePerHourRaw,
            long newTorrentsToday,
            long clientDiscoveriesToday,
            boolean newTorrentEnabled,
            boolean clientDiscoveryEnabled
    ) {
    }

    public record DynamicPointsSummary(
            String pointsTodayRaw,
            long averagePerHourRaw,
            String seedUploadPointsTodayRaw,
            String downloadPointsTodayRaw,
            String hotPeerPointsTodayRaw,
            boolean seedUploadEnabled,
            boolean downloadEnabled,
            boolean hotPeerEnabled
    ) {
    }

    public record FeatureFlags(
            boolean dynamicPoints,
            boolean swarmPeerInsights,
            boolean banPeerInsights
    ) {
    }

    public record PointsOverview(
            String balanceRaw,
            FixedPointsSummary fixed,
            OneTimePointsSummary oneTime,
            DynamicPointsSummary dynamic,
            FeatureFlags features,
            OffsetDateTime generatedAt
    ) {
    }

    public record PointsLogEntry(
            long id,
            OffsetDateTime occurredAt,
            String eventType,
            String pointsRaw,
            String fixedPointsRaw,
            String oneTimePointsRaw,
            String dynamicPointsRaw,
            Map<String, Object> details
    ) {
    }

    public record PointsLogPage(
            List<PointsLogEntry> records,
            long current,
            long size,
            long total,
            long pages
    ) {
    }
}
