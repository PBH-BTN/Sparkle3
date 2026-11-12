package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class MemberPlatformModels {
    private MemberPlatformModels() {
    }

    public record ProviderUpdateRequest(
            boolean enabled,
            String apiUrl,
            String accountId,
            String accessToken,
            Boolean clearAccessToken,
            String authHeader,
            String authScheme,
            String webhookToken,
            Boolean clearWebhookToken,
            String currency,
            String exchangeRate
    ) {
    }

    public record ProviderView(
            String provider,
            boolean enabled,
            String apiUrl,
            String accountId,
            boolean accessTokenConfigured,
            String authHeader,
            String authScheme,
            boolean webhookTokenConfigured,
            String currency,
            String exchangeRate,
            long bindingCount,
            long transactionCount,
            OffsetDateTime lastSyncedAt
    ) {
    }

    public record SettingsView(
            List<ProviderView> providers,
            String baseCurrency,
            String exchangeRateApiUrl
    ) {
    }
}
