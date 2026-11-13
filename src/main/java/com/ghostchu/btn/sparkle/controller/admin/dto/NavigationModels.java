package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;

public final class NavigationModels {
    private NavigationModels() {
    }

    public record PublicNavigationItem(
            String id,
            String labelZh,
            String labelEn,
            String href,
            String icon,
            boolean openInNewTab
    ) {
    }

    public record NavigationItemView(
            String id,
            boolean builtIn,
            String labelZh,
            String labelEn,
            String href,
            String icon,
            int sortOrder,
            boolean enabled,
            boolean openInNewTab,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long updatedBy
    ) {
    }

    public record NavigationItemInput(
            String labelZh,
            String labelEn,
            String href,
            String icon,
            int sortOrder,
            boolean enabled,
            boolean openInNewTab
    ) {
    }
}
