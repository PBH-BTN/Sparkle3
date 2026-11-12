package com.ghostchu.btn.sparkle.controller.ui.dto;

import com.ghostchu.btn.sparkle.controller.ui.user.dto.UserRelDto;
import com.ghostchu.btn.sparkle.controller.ui.userapp.dto.UserApplicationDto;
import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.PublicNavigationItem;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.service.UserPrivacySettings;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class UiModels {
    private UiModels() {
    }

    public record PrivacySettingsView(
            String avatarVisibility,
            String usernameVisibility,
            String pointsVisibility
    ) {
        public static PrivacySettingsView from(UserPrivacySettings settings) {
            return new PrivacySettingsView(
                    settings.avatarVisibility().name(),
                    settings.usernameVisibility().name(),
                    settings.pointsVisibility().name()
            );
        }
    }

    public record UserView(
            String id,
            String nickname,
            String email,
            String avatarUrl,
            OffsetDateTime registerAt,
            OffsetDateTime bannedAt,
            String bannedReason,
            String role,
            boolean admin,
            boolean moderator,
            PrivacySettingsView privacy
    ) {
        public static UserView from(User user, UserPrivacySettings privacy) {
            return new UserView(
                    String.valueOf(user.getId()),
                    user.getNickname(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    user.getRegisterAt(),
                    user.getBannedAt(),
                    user.getBannedReason(),
                    user.getRole(),
                    AdminPolicy.isAdmin(user),
                    AdminPolicy.hasModeratorAccess(user),
                    PrivacySettingsView.from(privacy)
            );
        }
    }

    public record TrafficSummary(
            long sentTrafficSelfReport,
            long receivedTrafficSelfReport,
            long sentTrafficOtherAck,
            long receivedTrafficOtherAck,
            OffsetDateTime lastUpdateAt,
            int refreshIntervalMinutes,
            int windowDays
    ) {
        public static TrafficSummary from(UserSwarmStatistic statistics,
                                          int refreshIntervalMinutes,
                                          int windowDays) {
            if (statistics == null) {
                return empty(refreshIntervalMinutes, windowDays);
            }
            return new TrafficSummary(
                    statistics.getSentTrafficSelfReport(),
                    statistics.getReceivedTrafficSelfReport(),
                    statistics.getSentTrafficOtherAck(),
                    statistics.getReceivedTrafficOtherAck(),
                    statistics.getLastUpdateAt(),
                    refreshIntervalMinutes,
                    windowDays
            );
        }

        public static TrafficSummary empty(int refreshIntervalMinutes, int windowDays) {
            return new TrafficSummary(0, 0, 0, 0, null, refreshIntervalMinutes, windowDays);
        }
    }

    public record SessionResponse(
            boolean authenticated,
            UserView user,
            TrafficSummary traffic,
            List<PublicNavigationItem> navigation
    ) {
        public static SessionResponse anonymous() {
            return new SessionResponse(false, null, null, List.of());
        }
    }

    public record HomeStats(
            Map<String, Long> banHistory,
            Map<String, Long> userApps,
            Map<String, Long> swarmTracker
    ) {
    }

    public record UserProfileResponse(
            UserView user,
            UserRelDto relation,
            String scoreBytesDisplay,
            String scoreBytesRaw
    ) {
    }

    public record PublicUserProfileResponse(
            String id,
            String nickname,
            String avatarUrl,
            PrivacySettingsView privacy,
            String scoreBytesDisplay,
            String scoreBytesRaw,
            boolean anonymous,
            boolean banned,
            boolean self,
            boolean canSendMessage,
            boolean canSendEmail
    ) {
    }

    public record ContactEmailRequest(
            String subject,
            String content
    ) {
    }

    public record ContactEmailResult(
            boolean sent,
            int remainingToday
    ) {
    }

    public record PrivacyResponse(PrivacySettingsView current) {
    }

    public record ApplicationCredentials(
            UserApplicationDto application,
            String appId,
            String appSecret
    ) {
    }

    public record RankingEntry(
            long rank,
            Long userId,
            String displayName,
            boolean anonymous,
            String avatarUrl,
            boolean avatarAvailable,
            boolean banned,
            long sentTrafficSelfReport,
            long receivedTrafficSelfReport,
            long sentTrafficOtherAck,
            long receivedTrafficOtherAck
    ) {
    }

    public record RankingResponse(
            RankingEntry self,
            List<RankingEntry> entries
    ) {
    }

    public record PointsRankingEntry(
            long rank,
            Long userId,
            String displayName,
            boolean anonymous,
            String avatarUrl,
            boolean avatarAvailable,
            boolean banned,
            String scoreBytes
    ) {
    }

    public record PointsRankingResponse(
            PointsRankingEntry self,
            List<PointsRankingEntry> entries
    ) {
    }

}
