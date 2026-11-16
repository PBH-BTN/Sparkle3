package com.ghostchu.btn.sparkle.service.membership;

/** A committed payment or account binding can change supporter eligibility. */
public record SupporterEligibilityChanged(Long userId, String provider, String externalUserId) {
    public static SupporterEligibilityChanged binding(long userId) {
        return new SupporterEligibilityChanged(userId, null, null);
    }

    public static SupporterEligibilityChanged payment(String provider, String externalUserId) {
        return new SupporterEligibilityChanged(null, provider, externalUserId);
    }
}
