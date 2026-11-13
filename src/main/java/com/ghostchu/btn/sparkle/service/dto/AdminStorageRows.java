package com.ghostchu.btn.sparkle.service.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class AdminStorageRows {
    private AdminStorageRows() {
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class BaseTableRow {
        private String schemaName;
        private String tableName;
        private Long estimatedRows;
        private Long heapBytes;
        private Long indexBytes;
        private Long totalBytes;
        private String reloptions;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class HypertableRow {
        private String hypertableSchema;
        private String hypertableName;
        private Boolean compressionEnabled;
        private Long numChunks;
        private String primaryDimension;
        private String primaryDimensionType;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class PolicyRow {
        private String hypertableSchema;
        private String hypertableName;
        private String procName;
        private String compressAfter;
        private String dropAfter;
        private BigDecimal scheduleSeconds;
        private OffsetDateTime nextStart;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class SizeRow {
        private Long tableBytes;
        private Long indexBytes;
        private Long toastBytes;
        private Long totalBytes;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class CompressionStatsRow {
        private Long totalChunks;
        private Long compressedChunks;
        private Long beforeBytes;
        private Long afterBytes;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class ChunkSummaryRow {
        private Long chunkCount;
        private Long compressedCount;
        private OffsetDateTime oldestStart;
        private OffsetDateTime newestEnd;
    }
}
