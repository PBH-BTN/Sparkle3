package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class StorageModels {
    private StorageModels() {
    }

    public record PolicyView(
            boolean enabled,
            String after,
            Double afterDays,
            Double scheduleHours,
            String nextStart
    ) {
        public static PolicyView disabled() {
            return new PolicyView(false, null, null, null, null);
        }
    }

    public record TableStorageView(
            String schemaName,
            String tableName,
            String qualifiedName,
            boolean hypertable,
            boolean compressionEnabled,
            String compressionMode,
            String timeColumn,
            String timeType,
            long estimatedRows,
            long chunks,
            long compressedChunks,
            long heapBytes,
            long indexBytes,
            long toastBytes,
            long totalBytes,
            long uncompressedBytes,
            long compressedBytes,
            String segmentBy,
            String orderBy,
            PolicyView compressionPolicy,
            PolicyView retentionPolicy
    ) {
    }

    public record StorageOverview(
            String timescaleVersion,
            long tableCount,
            long hypertableCount,
            long totalBytes,
            long uncompressedBytes,
            long compressedBytes,
            List<TableStorageView> tables
    ) {
    }

    public record BanHistoryDeletePreview(
            OffsetDateTime cutoff,
            boolean hypertable,
            long chunks,
            long compressedChunks,
            Long estimatedRows,
            OffsetDateTime oldestRangeStart,
            OffsetDateTime newestRangeEnd
    ) {
    }

    public record BanHistoryDeleteResult(
            OffsetDateTime cutoff,
            long affectedChunks,
            Long deletedRows,
            String message
    ) {
    }
}
