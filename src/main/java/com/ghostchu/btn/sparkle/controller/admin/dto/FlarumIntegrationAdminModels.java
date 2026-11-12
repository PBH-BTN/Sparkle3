package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;

public final class FlarumIntegrationAdminModels {
    private FlarumIntegrationAdminModels() {
    }

    public record SettingsView(
            boolean tokenConfigured,
            OffsetDateTime updatedAt,
            Long updatedBy
    ) {
    }

    public record UpdateTokenRequest(
            String token,
            Boolean clearToken
    ) {
    }
}
