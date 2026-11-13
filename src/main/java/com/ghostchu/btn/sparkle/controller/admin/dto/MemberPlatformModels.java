package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class MemberPlatformModels {
    private MemberPlatformModels() {
    }

    /**
     * Provider settings. Only the credential fields used by that provider are
     * read; the rest stay untouched.
     */
    public record ProviderUpdateRequest(
            boolean enabled,
            String accountId,
            String accessToken,
            Boolean clearAccessToken,
            String webhookToken,
            Boolean clearWebhookToken,
            String currency,
            String exchangeRate
    ) {
    }

    public record ProviderView(
            String provider,
            boolean supported,
            boolean enabled,
            boolean historySync,
            boolean webhook,
            String documentationUrl,
            List<String> configFields,
            String accountId,
            boolean accessTokenConfigured,
            boolean webhookTokenConfigured,
            boolean ready,
            String currency,
            String exchangeRate,
            long bindingCount,
            long transactionCount,
            OffsetDateTime lastSyncedAt,
            String lastRunStatus,
            OffsetDateTime lastRunAt,
            String lastRunError
    ) {
    }

    public record SettingsView(
            List<ProviderView> providers,
            String baseCurrency,
            String exchangeRateApiUrl
    ) {
    }

    public record SyncOutcome(
            String provider,
            boolean success,
            int records,
            int remoteCount,
            String message
    ) {
    }

    public record LicenseSettingsView(
            String donationLicenseAmount,
            int validYears,
            boolean privateKeyConfigured,
            String baseCurrency
    ) {
    }

    public record LicenseSettingsUpdateRequest(
            String donationLicenseAmount,
            int validYears,
            String privateKey,
            Boolean clearPrivateKey
    ) {
    }
}
