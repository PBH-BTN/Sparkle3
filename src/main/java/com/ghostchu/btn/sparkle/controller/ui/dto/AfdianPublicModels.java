package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class AfdianPublicModels {
    private AfdianPublicModels() {
    }

    public record BindRequest(
            String orderNo
    ) {
    }

    public record DonationView(
            String outTradeNo,
            String planId,
            String planTitle,
            String type,
            String amount,
            String status,
            boolean remotePresent,
            String remark,
            OffsetDateTime createdAt
    ) {
    }

    public record BindingView(
            boolean configured,
            boolean bound,
            String afdianUserId,
            String afdianName,
            String afdianAvatar,
            OffsetDateTime boundAt,
            OffsetDateTime lastSyncedAt,
            String totalAmount,
            long donationCount,
            List<DonationView> donations
    ) {
    }

    public record LicenseView(
            String licenseId,
            String sourceType,
            String sourceOrderNo,
            String planId,
            String consumedAmount,
            String licenseKey,
            OffsetDateTime issuedAt,
            OffsetDateTime startAt,
            OffsetDateTime expireAt,
            OffsetDateTime revokedAt,
            String revokedReason
    ) {
    }

    public record LicenseAvailabilityView(
            boolean configured,
            int availableProductLicenses,
            int availableDonationLicenses,
            int totalAvailable,
            String donationLicenseAmount,
            String baseCurrency,
            int validYears,
            List<LicenseView> licenses
    ) {
    }
}
