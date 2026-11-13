package com.ghostchu.btn.sparkle.service.membership;

import java.util.List;

/**
 * Result of a full history fetch.
 *
 * @param transactions        normalized transactions
 * @param discoveredAccountId account/campaign id resolved from the API, or null
 * @param remoteCount         transactions the provider reports, or -1 when unknown
 * @param complete            whether every transaction the provider reports was
 *                            actually fetched; a short history must not be used
 *                            to mark previously synced rows as deleted
 */
public record MemberPlatformSnapshot(
        List<PlatformTransaction> transactions,
        String discoveredAccountId,
        int remoteCount,
        boolean complete
) {
    public MemberPlatformSnapshot {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public MemberPlatformSnapshot(
            List<PlatformTransaction> transactions,
            String discoveredAccountId,
            int remoteCount
    ) {
        this(transactions, discoveredAccountId, remoteCount, true);
    }

    public MemberPlatformSnapshot(
            List<PlatformTransaction> transactions,
            String discoveredAccountId
    ) {
        this(transactions, discoveredAccountId, -1, true);
    }
}
