package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class AiModerationAdminModels {
    private AiModerationAdminModels() {
    }

    public record AiModerationSettingsView(
            boolean enabled,
            String baseUrl,
            boolean apiKeyConfigured,
            String model,
            String rubric,
            int timeoutSeconds,
            int maxAttempts,
            boolean moderateAvatars
    ) {
    }

    public record IpQueryCommentSettingsView(
            boolean commentsEnabled,
            boolean commentsVisible,
            int maxLength,
            int hourlyLimit
    ) {
    }

    public record ModerationMetricsView(
            long pending,
            long running,
            long approved,
            long rejected,
            long failed
    ) {
    }

    public record ModerationJobView(
            long id,
            String contentType,
            long contentRefId,
            long contentVersion,
            Long subjectUserId,
            String status,
            int attemptCount,
            int maxAttempts,
            String providerModel,
            String rejectionReason,
            String lastError,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {
    }

    public record AiModerationAdminView(
            AiModerationSettingsView settings,
            IpQueryCommentSettingsView comments,
            ModerationMetricsView metrics,
            List<ModerationJobView> recentJobs,
            OffsetDateTime generatedAt
    ) {
    }

    public record AiModerationSettingsUpdate(
            boolean enabled,
            String baseUrl,
            String apiKey,
            boolean clearApiKey,
            String model,
            String rubric,
            int timeoutSeconds,
            int maxAttempts,
            boolean moderateAvatars
    ) {
    }

    public record IpQueryCommentSettingsUpdate(
            boolean commentsEnabled,
            boolean commentsVisible,
            int maxLength,
            int hourlyLimit
    ) {
    }

    public record ModerationTestRequest(String content) {
    }

    public record ModerationTestResult(
            boolean approved,
            String reason,
            List<String> categories,
            double confidence
    ) {
    }

    public record RequeueResult(int jobsCreated) {
    }
}
