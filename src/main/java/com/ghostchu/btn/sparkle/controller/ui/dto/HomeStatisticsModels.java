package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class HomeStatisticsModels {
    private HomeStatisticsModels() {
    }

    public record ChartDatum(
            String name,
            long value
    ) {
    }

    public record ChartSeries(
            String name,
            List<Long> values
    ) {
    }

    public record DistributionChart(
            List<ChartDatum> data
    ) {
    }

    public record StackedAreaChart(
            List<String> categories,
            List<ChartSeries> series
    ) {
    }

    public record HomeNetworkStatisticsResponse(
            int days,
            String source,
            DistributionChart peerIds,
            DistributionChart clientNames,
            StackedAreaChart clientNameTrend,
            StackedAreaChart geoCities,
            StackedAreaChart ipVersions,
            OffsetDateTime generatedAt
    ) {
    }

    public record HomeCompanionStatisticsResponse(
            DistributionChart companionVersions,
            DistributionChart companionIpVersions,
            DistributionChart companionGeoIp,
            OffsetDateTime generatedAt
    ) {
    }
}
