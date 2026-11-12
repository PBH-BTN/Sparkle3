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
                true,
                category.isMandatory()
        );
    }
}
