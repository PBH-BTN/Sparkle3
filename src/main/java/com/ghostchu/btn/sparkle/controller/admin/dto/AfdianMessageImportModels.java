package com.ghostchu.btn.sparkle.controller.admin.dto;

public final class AfdianMessageImportModels {
    private AfdianMessageImportModels() { }

    public record ImportRequest(String authToken, String verificationPrivateKey) {
        @Override
        public String toString() { return "ImportRequest[redacted]"; }
    }

    public record ImportStatus(String state, int dialogs, int messages, int imported,
                               int duplicates, int unmatched, int reconciled, int conflicts) { }

    public record PendingLicense(String licenseId, String externalUserId,
                                 java.time.OffsetDateTime issuedAt) { }

    public record MatchRequest(java.util.UUID licenseId, String orderId) { }
}
