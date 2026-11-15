package com.ghostchu.btn.sparkle.util;

import java.time.OffsetDateTime;

public final class SuspensionStatus {
    public static final String SUSPENDED_SQL = "(banned_at IS NOT NULL AND (unban_at IS NULL OR unban_at > CURRENT_TIMESTAMP))";
    public static final String NOT_SUSPENDED_SQL = "(banned_at IS NULL OR unban_at <= CURRENT_TIMESTAMP)";

    private SuspensionStatus() {
    }

    public static boolean isActive(OffsetDateTime bannedAt, OffsetDateTime unbanAt, OffsetDateTime now) {
        return bannedAt != null && (unbanAt == null || unbanAt.isAfter(now));
    }
}
