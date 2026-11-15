package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;

public final class BanSubmissionModels {
    private BanSubmissionModels() {}

    public record ClearResult(int deleted) {}

    public record AppRank(String appId, String ownerId, String lastUserAgent, long detectionHits, long replays) {}

    public record Event(String id, String appId, String peerNetwork, int peerPort, String moduleName, String ruleName,
                        OffsetDateTime firstSeen, OffsetDateTime lastSeen, int peakWindows, long replays,
                        long suppressed, String status) {}
}
