package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class MemberPlatformAdminModels {
    private MemberPlatformAdminModels() {
    }

    public record TransactionView(
            long id,
            String provider,
            String transactionId,
            String externalUserId,
            String externalUserName,
            String type,
            String sku,
            String productName,
            BigDecimal amount,
            String currency,
            BigDecimal amountCny,
            BigDecimal amountBase,
            String baseCurrency,
            String status,
            OffsetDateTime occurredAt,
            boolean remotePresent,
            boolean licenseConsumed,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastReconciledAt,
            OffsetDateTime lastChangedAt
    ) {
    }

    public record TransactionPage(
            List<TransactionView> items,
            long total,
            long page,
            long size
    ) {
    }

    public record SyncRunView(
            long id,
            String provider,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            int pagesFetched,
            int recordsSeen,
            int insertedCount,
            int updatedCount,
            int unchangedCount,
            int missingCount,
            String errorMessage
    ) {
    }

    public record AuditEventView(
            long id,
            long syncRunId,
            String provider,
            String transactionId,
            String eventType,
            List<String> changedFields,
            OffsetDateTime createdAt
    ) {
    }

    public record ReconciliationView(
            List<SyncRunView> runs,
            List<AuditEventView> events
    ) {
    }

    public record LicenseAdminView(
            String licenseId,
            String btnUserId,
            String btnNickname,
            String provider,
            String sourceType,
            String sourceOrderNo,
            String planId,
            String consumedAmount,
            String status,
            OffsetDateTime issuedAt,
            OffsetDateTime startAt,
            OffsetDateTime expireAt,
            OffsetDateTime revokedAt,
            String revokedReason
    ) {
    }

    public record LicensePage(
            List<LicenseAdminView> items,
            long total,
            long page,
            long size,
            String baseCurrency
    ) {
    }
}
