package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class BittorrentTrafficModels {
    private BittorrentTrafficModels() {
    }

    public record TrafficTotals(
            String uploadBytes,
            String downloadBytes,
            String transferBytes
    ) {
    }

    public record TrafficCity(
            String name,
            String bytes
    ) {
    }

    public record TrafficDistribution(
            List<TrafficCity> data,
            String totalBytes
    ) {
    }

    public record HomeBittorrentTrafficResponse(
            int days,
            TrafficTotals totals,
            TrafficDistribution topUploadCities,
            TrafficDistribution topDownloadCities,
            OffsetDateTime generatedAt
    ) {
    }
}
