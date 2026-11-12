package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class MemberPlatformPublicModels {
    private MemberPlatformPublicModels() {
    }

    public record BindRequest(
            String proof
    ) {
    }

    public record TransactionView(
            String transactionId,
            String type,
            String sku,
            String productName,
            String amount,
            String currency,
            String amountCny,
            String amountBase,
            String baseCurrency,
            String status,
            boolean remotePresent,
            OffsetDateTime occurredAt
    ) {
    }

    public record ProviderBindingView(
            boolean configured,
            boolean bound,
            String provider,
            String externalUserId,
            String externalUserName,
            String externalUserAvatar,
            String proof,
            OffsetDateTime boundAt,
            OffsetDateTime lastSyncedAt,
            String lastError,
            String totalCny,
            String totalBase,
            String baseCurrency,
            long donationCount,
            long productCount,
            List<TransactionView> transactions
    ) {
    }
}
