package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;

public final class AnnouncementModels {
    private AnnouncementModels() {
    }

    public record AnnouncementView(
            long id,
            String level,
            String placement,
            String titleZh,
            String titleEn,
            String contentZh,
            String contentEn,
            boolean enabled,
            boolean dismissible,
            int sortOrder,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long updatedBy
    ) {
    }

    public record PublicAnnouncementView(
            long id,
            String level,
            String placement,
            String titleZh,
            String titleEn,
            String contentZh,
            String contentEn,
            boolean dismissible,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AnnouncementInput(
            String level,
            String placement,
            String titleZh,
            String titleEn,
            String contentZh,
            String contentEn,
            boolean enabled,
            boolean dismissible,
            int sortOrder,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            boolean sendInbox
    ) {
    }
}
