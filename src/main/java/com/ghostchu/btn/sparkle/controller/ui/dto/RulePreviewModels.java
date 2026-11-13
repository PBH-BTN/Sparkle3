package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class RulePreviewModels {
    private RulePreviewModels() {
    }

    public record RulePreviewEntry(
            String source,
            String category,
            String content
    ) {
    }

    public record RulePreviewSection(
            String type,
            List<RulePreviewEntry> entries
    ) {
    }

    public record RulePreviewResponse(
            List<RulePreviewSection> sections,
            OffsetDateTime generatedAt
    ) {
    }
}
