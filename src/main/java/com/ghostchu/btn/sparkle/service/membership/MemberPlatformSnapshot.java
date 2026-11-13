package com.ghostchu.btn.sparkle.service.membership;

import java.util.List;

/**
 * Result of a full history fetch.
 *
 * @param transactions       normalized transactions
 * @param discoveredAccountId account/campaign id resolved from the API, or null
 */
public record MemberPlatformSnapshot(
        List<PlatformTransaction> transactions,
        String discoveredAccountId
) {
    public MemberPlatformSnapshot {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }
}
