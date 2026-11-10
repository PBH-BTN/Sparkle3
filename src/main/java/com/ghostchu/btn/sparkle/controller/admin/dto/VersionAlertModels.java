package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;

public final class VersionAlertModels {
    private VersionAlertModels() {
    }

    public record VersionAlertRule(
            String id,
            String name,
            boolean enabled,
            String product,
            String versionRequirement,
            String severity,
            String subject,
            String body,
            boolean oncePerUser,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long updatedBy
    ) {
    }

    public record VersionAlertRuleInput(
            String name,
            boolean enabled,
            String product,
            String versionRequirement,
            String severity,
            String subject,
            String body,
            boolean oncePerUser
    ) {
    }

    public record VersionAlertMatch(
            boolean matched,
            String product,
            String version,
            String versionRequirement,
            String userAgent
    ) {
    }

    public record VersionAlertDelivery(
            String id,
            String ruleId,
            String ruleName,
            Long userId,
            String email,
            String nickname,
            String product,
            String version,
            String userAgent,
            String status,
            String error,
            String trigger,
            OffsetDateTime sentAt
    ) {
    }
}
