package com.ghostchu.btn.sparkle.constants;

import java.util.Arrays;
import java.util.Optional;

public enum NotificationCategory {
    LEGAL_COMPLIANCE(true),
    ACCOUNT_SECURITY(true),
    CLIENT_SECURITY(false),
    MAJOR_ANNOUNCEMENT(false),
    DAILY_ANNOUNCEMENT(false),
    CHANGELOG(false),
    DIRECT_MESSAGE(false),
    SOCIAL_ALERT(false),
    GENERAL_NOTICE(false);

    private final boolean mandatory;

    NotificationCategory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public static Optional<NotificationCategory> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
