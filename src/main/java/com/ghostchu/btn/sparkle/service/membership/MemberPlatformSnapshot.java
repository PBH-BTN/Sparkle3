package com.ghostchu.btn.sparkle.service.membership;

import java.util.List;

/**
 * Result of a full history fetch.
 *
 * @param transactions        normalized transactions
 * @param discoveredAccountId account/campaign id resolved from the API, or null
 * @param remoteCount         transactions the provider reports, or -1 when unknown
 */
public record MemberPlatformSnapshot(
        List<PlatformTransaction> transactions,
        String discoveredAccountId,
        int remoteCount
) {
    public MemberPlatformSnapshot {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public MemberPlatformSnapshot(
            List<PlatformTransaction> transactions,
            String discoveredAccountId
    ) {
        this(transactions, discoveredAccountId, -1);
    }
}
