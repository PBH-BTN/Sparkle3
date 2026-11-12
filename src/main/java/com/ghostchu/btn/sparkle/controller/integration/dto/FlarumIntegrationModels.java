package com.ghostchu.btn.sparkle.controller.integration.dto;

import java.time.OffsetDateTime;

public final class FlarumIntegrationModels {
    private FlarumIntegrationModels() {
    }

    public record UserDataResponse(
            boolean bound,
            String githubUid,
            String btnUserId,
            BasicInfo basicInfo,
            Traffic7d traffic7d,
            Points points,
            Rankings rankings,
            OffsetDateTime generatedAt
    ) {
        public static UserDataResponse unbound(String githubUid) {
            return new UserDataResponse(
                    false,
                    githubUid,
                    null,
                    null,
                    null,
                    null,
                    null,
                    OffsetDateTime.now()
            );
        }
    }

    public record BasicInfo(
            String id,
            String nickname,
            OffsetDateTime registeredAt,
            OffsetDateTime lastLoginAt,
            boolean banned
    ) {
    }

    public record Traffic7d(
            boolean enabled,
            int windowDays,
            String sentTrafficSelfReport,
            String receivedTrafficSelfReport,
            String sentTrafficOtherAck,
            String receivedTrafficOtherAck,
            OffsetDateTime lastUpdatedAt
    ) {
    }

    public record Points(String balanceBytes) {
    }

    public record Rankings(
            TrafficRanking traffic,
            PointsRanking points
    ) {
    }

    public record TrafficRanking(
            Long rank,
            String sentTrafficSelfReport,
            String receivedTrafficSelfReport,
            String sentTrafficOtherAck,
            String receivedTrafficOtherAck
    ) {
    }

    public record PointsRanking(
            Long rank,
            String balanceBytes
    ) {
    }
}
