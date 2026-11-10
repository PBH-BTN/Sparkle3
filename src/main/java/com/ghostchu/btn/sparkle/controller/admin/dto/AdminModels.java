package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class AdminModels {
    private AdminModels() {
    }

    public record PageResult<T>(
            List<T> records,
            long current,
            long size,
            long total,
            long pages
    ) {
    }

    public record SystemSample(
            long timestamp,
            double cpuUsage,
            double memoryUsage,
            long memoryUsed,
            long memoryTotal,
            double diskUsage,
            long diskUsed,
            long diskTotal
    ) {
    }

    public record JvmInfo(
            int availableProcessors,
            double systemLoadAverage,
            long uptimeMillis,
            long heapUsed,
            long heapMax,
            int threadCount,
            String javaVersion
    ) {
    }

    public record DashboardResponse(
            SystemSample current,
            List<SystemSample> history,
            List<ModuleMetric> modules,
            JvmInfo jvm,
            OffsetDateTime generatedAt
    ) {
    }

    public record ModuleMetric(
            String key,
            long value
    ) {
    }

    public record AdminUserView(
            Long id,
            String email,
            String nickname,
            String avatarUrl,
            String role,
            String privacyLevel,
            OffsetDateTime registerAt,
            OffsetDateTime lastLoginAt,
            OffsetDateTime bannedAt,
            String bannedReason,
            String githubLogin
    ) {
    }

    public record AdminApplicationView(
            Long id,
            String appId,
            String comment,
            Long ownerId,
            String ownerNickname,
            String ownerEmail,
            OffsetDateTime createdAt,
            OffsetDateTime lastSeenAt,
            OffsetDateTime bannedAt,
            String bannedReason,
            Boolean btnBypass,
            String installationId,
            String lastUserAgent
    ) {
    }

    public record ApplicationCredentials(
            AdminApplicationView application,
            String appId,
            String appSecret
    ) {
    }

    public record RuleSummary(
            String type,
            long count
    ) {
    }

    public record RuntimeConfigView(
            int protocolMinVersion,
            int protocolMaxVersion,
            boolean queryIpPowCaptcha,
            boolean clientDiscoveryEnabled,
            String trackerEndpoint
    ) {
    }
}
