package com.ghostchu.btn.sparkle.service.mail;

import java.time.OffsetDateTime;

public record MailIngestionStatus(
        boolean running,
        OffsetDateTime lastScanAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime nextScanAt,
        int lastImported,
        int lastSkipped,
        int lastFailed,
        long totalImported,
        String lastError
) {
    public static MailIngestionStatus initial() {
        return new MailIngestionStatus(
                false, null, null, null, 0, 0, 0, 0, null
        );
    }
}
