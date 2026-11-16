package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DataExportJob(UUID id, long userId, Category category, String filters, String language,
                            OffsetDateTime requestedAt, int maxRows, long maxBytes, int retentionHours,
                            String status, OffsetDateTime expiresAt, Long fileBytes, String downloadDigest) {
}
