package com.ghostchu.btn.sparkle.service.account;

import java.time.OffsetDateTime;

public record AccountDeletionNotificationEvent(
        long userId,
        String recipientEmail,
        String nickname,
        Kind kind,
        OffsetDateTime requestedAt,
        OffsetDateTime coolingEndsAt,
        OffsetDateTime completedAt,
        String cancellationDetail
) {
    public enum Kind {
        STARTED,
        CANCELLED,
        COMPLETED
    }
}
