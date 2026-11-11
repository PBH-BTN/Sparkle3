package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class SwarmDownloadStatisticsModels {
    private SwarmDownloadStatisticsModels() {
    }

    public record SwarmDownloadCidrEntry(
            String cidr,
            int ipVersion,
            String downloadBytes,
            long reporterCount,
            long connectionCount,
            long torrentCount,
            OffsetDateTime latestSeenAt
    ) {
    }

    public record SwarmDownloadCidrResponse(
            int hours,
            int ipv4Prefix,
            int ipv6Prefix,
            String ipVersion,
            String thresholdTb,
            String thresholdBytes,
            List<SwarmDownloadCidrEntry> entries,
            long matchingGroups,
            boolean truncated,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            OffsetDateTime generatedAt
    ) {
    }
}
