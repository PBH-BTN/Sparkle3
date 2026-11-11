package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class PeerInsightsModels {
    private PeerInsightsModels() {
    }

    public record PeerVersionCount(
            String name,
            long peers
    ) {
    }

    public record PeerTorrentSeries(
            String torrentId,
            List<Long> values
    ) {
    }

    public record SwarmPeerInsightsResponse(
            boolean enabled,
            int days,
            List<PeerVersionCount> ipVersions,
            List<String> categories,
            List<PeerTorrentSeries> series,
            OffsetDateTime generatedAt
    ) {
    }

    public record BanPeerInsightsResponse(
            boolean enabled,
            int days,
            List<PeerVersionCount> ipVersions,
            OffsetDateTime generatedAt
    ) {
    }
}
