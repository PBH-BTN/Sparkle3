package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;

public record NotificationPreference(
        NotificationCategory category,
        boolean inboxEnabled,
        boolean emailEnabled,
        boolean locked
) {
    public static NotificationPreference defaults(NotificationCategory category) {
        return new NotificationPreference(
                category,
                true,
                switch (category) {
                    case GENERAL_NOTICE, CHANGELOG, DAILY_ANNOUNCEMENT -> false;
                    default -> true;
                },
                category.isMandatory()
        );
    }
}
