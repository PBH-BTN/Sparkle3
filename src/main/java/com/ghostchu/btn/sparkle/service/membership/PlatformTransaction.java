package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Provider-neutral transaction produced by a platform adapter.
 *
 * <p>Adapters own API access and payload interpretation; the service layer
 * converts these values into ledger rows, applies exchange rates, and keeps
 * reconciliation bookkeeping.</p>
 */
public record PlatformTransaction(
        String transactionId,
        String externalUserId,
        String externalUserName,
        String externalUserAvatar,
        String type,
        String sku,
        String productName,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime occurredAt,
        JsonNode raw
) {
    public static final String PRODUCT = "PRODUCT";
    public static final String DONATION = "DONATION";

    public boolean isProduct() {
        return PRODUCT.equals(type);
    }

    public static String providerName(MemberPlatform provider) {
        return provider == null ? "" : provider.name();
    }
}
