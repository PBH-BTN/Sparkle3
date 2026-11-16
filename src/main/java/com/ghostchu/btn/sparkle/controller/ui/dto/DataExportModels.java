package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.util.Map;

import java.util.UUID;

public final class DataExportModels {
    private DataExportModels() {
    }

    public enum Category {
        BANS, CLIENTS, SWARM, TRAFFIC, SUBMISSIONS
    }

    public record ExportRequest(Category category, Map<String, Object> filters, String language) {
    }

    public record ExportOptions(int slotsAvailable, String prompt) {
    }

    public record AcceptedExport(UUID id) {
    }

    public record Settings(Integer maxRows, Integer maxFileMiB, Integer retentionHours,
                           Integer downloadMbps, Integer dailySlots, Integer poolMiB,
                           String promptZh, String promptEn) {
        public long maxBytes() {
            return maxFileMiB.longValue() * 1_048_576;
        }

        public long poolBytes() {
            return poolMiB.longValue() * 1_048_576;
        }
    }
}
