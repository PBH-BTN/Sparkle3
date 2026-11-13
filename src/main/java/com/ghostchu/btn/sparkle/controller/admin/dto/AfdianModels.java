package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class AfdianModels {
    private AfdianModels() {
    }

    public record SettingsUpdateRequest(
            String userId,
            String accessToken,
            Boolean clearToken,
            String apiBaseUrl,
            boolean syncEnabled
    ) {
    }

    public record BindingView(
            long btnUserId,
            String btnNickname,
            String afdianUserId,
            String afdianName,
            String afdianAvatar,
            String boundOrderNo,
            OffsetDateTime boundAt,
            OffsetDateTime lastSyncedAt,
            String lastError,
            String totalAmount,
            long donationCount
    ) {
    }

    public record DonationView(
            String outTradeNo,
            String afdianUserId,
            String afdianUserName,
            String planId,
            String planTitle,
            String totalAmount,
            String showAmount,
            int status,
            String remark,
            OffsetDateTime createdAt,
            OffsetDateTime syncedAt
    ) {
    }

    public record SettingsView(
            String userId,
            boolean tokenConfigured,
            boolean configured,
            String apiBaseUrl,
            boolean syncEnabled,
            String baseCurrency,
            OffsetDateTime lastSyncedAt,
            List<BindingView> bindings,
            List<DonationView> recentDonations,
            List<LicenseView> recentLicenses
    ) {
    }

    public record LicenseView(
            String licenseId,
            long btnUserId,
            String btnNickname,
            String afdianUserId,
            String sourceType,
            String sourceOrderNo,
            String planId,
            String consumedAmount,
            OffsetDateTime issuedAt,
            OffsetDateTime expireAt,
            OffsetDateTime revokedAt,
            String revokedReason
    ) {
    }

    public record SyncResult(
            boolean success,
            int pages,
            int ordersSeen,
            int completedOrders,
            int bindingsUpdated,
            String message
    ) {
    }
}
