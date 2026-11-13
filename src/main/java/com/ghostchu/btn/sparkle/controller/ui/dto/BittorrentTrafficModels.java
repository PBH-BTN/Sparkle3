package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class BittorrentTrafficModels {
    private BittorrentTrafficModels() {
    }

    public record TrafficRanking(String name, String uploadBytes, String downloadBytes) {}

    public record TrafficRankingsResponse(int days, List<TrafficRanking> peerIds,
                                          List<TrafficRanking> clientNames, OffsetDateTime generatedAt) {}

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
