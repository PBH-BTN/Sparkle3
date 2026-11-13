package com.ghostchu.btn.sparkle.service.membership;

import com.ghostchu.btn.sparkle.constants.MemberPlatform;

import java.math.BigDecimal;

/**
 * Administrator credentials and settlement settings for one provider.
 */
public record MemberPlatformCredentials(
        MemberPlatform provider,
        String accessToken,
        String accountId,
        String webhookToken,
        String currency,
        BigDecimal exchangeRate
) {
    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    public boolean hasWebhookToken() {
        return webhookToken != null && !webhookToken.isBlank();
    }
}
