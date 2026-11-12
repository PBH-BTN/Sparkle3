package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.BanHistoryDeletePreview;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.BanHistoryDeleteResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.PolicyView;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.StorageOverview;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.TableStorageView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AdminStorageMapper;
import com.ghostchu.btn.sparkle.mapper.AdminStorageMapper.SqlOptionValue;
import com.ghostchu.btn.sparkle.mapper.AdminStorageMapper.SqlRelation;
import com.ghostchu.btn.sparkle.service.impl.StatisticsRefreshServiceImpl;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStorageService {
    private static final String BAN_HISTORY_SCHEMA = "public";
    private static final String BAN_HISTORY_TABLE = "ban_history";
    private static final String DELETE_CONFIRMATION = "DELETE BAN_HISTORY";
    private static final Pattern IDENTIFIER_LIST = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*(\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)*$"
    );
    private static final Pattern ORDER_BY_LIST = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*(\\s+(ASC|DESC))?(\\s+NULLS\\s+(FIRST|LAST))?"
                    + "(\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*(\\s+(ASC|DESC))?(\\s+NULLS\\s+(FIRST|LAST))?)*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INTERVAL_PART = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(years?|months?|weeks?|days?|hours?|minutes?|mins?)"
    );

    private final AdminStorageMapper adminStorageMapper;
    private final StatisticsRefreshServiceImpl statisticsRefreshService;
    private final TaskStatusService taskStatusService;

    @PostConstruct
    void registerTaskDefinitions() {
        taskStatusService.ensure(TaskDefinition.event(
                "ban-history-statistics-refresh",
                TaskGroup.STATISTICS,
                "admin.tasks.items.ban-history-statistics-refresh.name",
                "admin.tasks.items.ban-history-statistics-refresh.description"
        ));
    }

    public @NotNull StorageOverview overview() {
        TimescaleSupport support = timescaleSupport();
        Map<String, HypertableMeta> hypertables = loadHypertables();
        Map<String, Map<String, Object>> policies = loadPolicies();

        List<BaseTable> baseTables = adminStorageMapper.selectBaseTables().stream()
                .map(row -> new BaseTable(
                        row.getSchemaName(),
                        row.getTableName(),
                        valueOrZero(row.getEstimatedRows()),
                        valueOrZero(row.getHeapBytes()),
                        valueOrZero(row.getIndexBytes()),
                        valueOrZero(row.getTotalBytes()),
                        row.getReloptions()
                ))
                .toList();

        List<TableStorageView> tables = new ArrayList<>(baseTables.size());
        for (BaseTable table : baseTables) {
            String key = tableKey(table.schemaName(), table.tableName());
            HypertableMeta hyper = hypertables.get(key);
            TableStorageView view = hyper == null
                    ? regularTable(table)
                    : hypertable(table, hyper, support, policies);
            tables.add(view);
        }
        tables.sort(Comparator.comparingLong(TableStorageView::totalBytes).reversed());
        long totalBytes = tables.stream().mapToLong(TableStorageView::totalBytes).sum();
        long uncompressedBytes = tables.stream().mapToLong(TableStorageView::uncompressedBytes).sum();
        long compressedBytes = tables.stream().mapToLong(TableStorageView::compressedBytes).sum();
        return new StorageOverview(
                support.version(),
                tables.size(),
                hypertables.size(),
                totalBytes,
                uncompressedBytes,
                compressedBytes,
                tables
        );
    }

    @Transactional
    public TableStorageView updateCompressionPolicy(
            String schemaName,
            String tableName,
            boolean enabled,
            int compressAfterDays,
            int scheduleHours,
            String segmentBy,
            String orderBy
    ) throws BusinessException {
        HypertableMeta hypertable = requireHypertable(schemaName, tableName);
        TimescaleSupport support = timescaleSupport();
        validatePolicyValues(enabled, compressAfterDays, scheduleHours);
        String safeSegmentBy = validateSegmentBy(schemaName, tableName, segmentBy, true);
        String safeOrderBy = validateOrderBy(schemaName, tableName, orderBy, true);
        String relation = qualified(schemaName, tableName);
        SqlRelation sqlRelation = new SqlRelation(schemaName, tableName);
        SqlOptionValue sqlOrderBy = new SqlOptionValue(safeOrderBy);
        SqlOptionValue sqlSegmentBy = safeSegmentBy.isBlank() ? null : new SqlOptionValue(safeSegmentBy);

        try {
            if (support.columnstoreStats()) {
                if (sqlSegmentBy == null) {
                    adminStorageMapper.setColumnstoreOptionsWithoutSegment(
                            sqlRelation,
                            sqlOrderBy
                    );
                } else {
                    adminStorageMapper.setColumnstoreOptionsWithSegment(
                            sqlRelation,
                            sqlSegmentBy,
                            sqlOrderBy
                    );
                }
                adminStorageMapper.removeColumnstorePolicy(relation);
                if (enabled) {
                    adminStorageMapper.addColumnstorePolicy(
                            relation,
                            compressAfterDays,
                            scheduleHours
                    );
                }
            } else {
                if (sqlSegmentBy == null) {
                    adminStorageMapper.setLegacyCompressionOptionsWithoutSegment(
                            sqlRelation,
                            sqlOrderBy
                    );
                } else {
                    adminStorageMapper.setLegacyCompressionOptionsWithSegment(
                            sqlRelation,
                            sqlSegmentBy,
                            sqlOrderBy
                    );
                }
                adminStorageMapper.removeCompressionPolicy(relation);
                if (enabled) {
                    adminStorageMapper.addCompressionPolicy(
                            relation,
                            compressAfterDays,
                            scheduleHours
                    );
                }
            }
        } catch (DataAccessException exception) {
            throw policyFailure(exception);
        }
        return findTable(schemaName, tableName);
    }

    @Transactional
    public TableStorageView updateRetentionPolicy(
            String schemaName,
            String tableName,
            boolean enabled,
            int dropAfterDays,
            int scheduleHours
    ) throws BusinessException {
        requireHypertable(schemaName, tableName);
        validatePolicyValues(enabled, dropAfterDays, scheduleHours);
        String relation = qualified(schemaName, tableName);
        try {
            adminStorageMapper.removeRetentionPolicy(relation);
            if (enabled) {
                adminStorageMapper.addRetentionPolicy(
                        relation,
                        dropAfterDays,
                        scheduleHours
                );
            }
        } catch (DataAccessException exception) {
            throw policyFailure(exception);
        }
        return findTable(schemaName, tableName);
    }

    public @NotNull BanHistoryDeletePreview previewBanHistoryDelete(@NotNull OffsetDateTime cutoff)
            throws BusinessException {
        validateCutoff(cutoff);
        HypertableMeta hyper = loadHypertables().get(tableKey(BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE));
        if (hyper == null) {
            long count = valueOrZero(adminStorageMapper.countBanHistoryBefore(cutoff));
            return new BanHistoryDeletePreview(cutoff, false, 0, 0, count, null, null);
        }
        try {
            var summary = adminStorageMapper.selectChunkSummary(
                    BAN_HISTORY_SCHEMA,
                    BAN_HISTORY_TABLE,
                    cutoff
            );
            if (summary == null) {
                return new BanHistoryDeletePreview(cutoff, true, 0, 0, null, null, null);
            }
            return new BanHistoryDeletePreview(
                    cutoff,
                    true,
                    valueOrZero(summary.getChunkCount()),
                    valueOrZero(summary.getCompressedCount()),
                    null,
                    summary.getOldestStart(),
                    summary.getNewestEnd()
            );
        } catch (DataAccessException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "Unable to inspect TimescaleDB chunks: "
                    + exception.getMostSpecificCause().getMessage());
        }
    }

    @Transactional
    public @NotNull BanHistoryDeleteResult deleteBanHistory(
            @NotNull OffsetDateTime cutoff,
            @Nullable String confirmation
    ) throws BusinessException {
        validateCutoff(cutoff);
        if (!DELETE_CONFIRMATION.equals(confirmation)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid deletion confirmation");
        }
        HypertableMeta hyper = loadHypertables().get(tableKey(BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE));
        long affected;
        Long deletedRows = null;
        if (hyper != null) {
            List<String> dropped = adminStorageMapper.dropBanHistoryChunks(
                    qualified(BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE),
                    cutoff
            );
            affected = dropped.size();
            adminStorageMapper.analyzeBanHistory();
        } else {
            affected = adminStorageMapper.deleteBanHistoryBefore(cutoff);
            deletedRows = affected;
        }
        refreshStatisticsAfterCommit();
        return new BanHistoryDeleteResult(
                cutoff,
                affected,
                deletedRows,
                hyper == null
                        ? "Rows deleted. Physical disk space may be reclaimed after VACUUM."
                        : "Older chunks dropped. Physical disk space may be reclaimed by PostgreSQL."
        );
    }

    private TableStorageView findTable(String schemaName, String tableName) {
        return overview().tables().stream()
                .filter(table -> table.schemaName().equals(schemaName) && table.tableName().equals(tableName))
                .findFirst()
                .orElseThrow();
    }

    private void refreshStatisticsAfterCommit() {
        Runnable refresh = () -> Thread.startVirtualThread(() -> {
            TaskStatusService.TaskExecution execution = taskStatusService.begin(
                    "ban-history-statistics-refresh",
                    "AFTER_DELETE"
            );
            try {
                statisticsRefreshService.onBanCountRefresh();
                execution.success("Ban history statistics refreshed");
            } catch (Exception exception) {
                execution.failure(exception);
                log.warn("Unable to refresh ban history statistics after manual deletion: {}",
                        exception.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refresh.run();
                }
            });
        } else {
            refresh.run();
        }
    }

    private TableStorageView regularTable(BaseTable table) {
        long toast = Math.max(0, table.totalBytes() - table.heapBytes() - table.indexBytes());
        return new TableStorageView(
                table.schemaName(),
                table.tableName(),
                table.schemaName() + "." + table.tableName(),
                false,
                false,
                "NONE",
                null,
                null,
                table.estimatedRows(),
                0,
                0,
                table.heapBytes(),
                table.indexBytes(),
                toast,
                table.totalBytes(),
                table.totalBytes(),
                0,
                null,
                null,
                PolicyView.disabled(),
                PolicyView.disabled()
        );
    }

    private TableStorageView hypertable(BaseTable table,
                                        HypertableMeta hyper,
                                        TimescaleSupport support,
                                        Map<String, Map<String, Object>> policies) {
        SizeBreakdown detailed = detailedSize(table.schemaName(), table.tableName());
        CompressionStats compression = compressionStats(table.schemaName(), table.tableName(), support);
        Map<String, Object> policy = policies.getOrDefault(tableKey(table.schemaName(), table.tableName()), Map.of());
        PolicyView compressionPolicy = policyView(policy.get("compression"));
        PolicyView retentionPolicy = policyView(policy.get("retention"));
        boolean compressionEnabled = hyper.compressionEnabled()
                || compression.totalChunks() > 0
                || compressionPolicy.enabled()
                || hasCompressionSettings(table.reloptions());
        String compressionMode = compressionEnabled
                ? (support.columnstoreStats() ? "COLUMNSTORE" : "COMPRESSION")
                : "NONE";
        long uncompressed = compression.beforeCompressionBytes() > 0
                ? compression.beforeCompressionBytes()
                : detailed.totalBytes();
        long compressed = compression.afterCompressionBytes();
        return new TableStorageView(
                table.schemaName(),
                table.tableName(),
                table.schemaName() + "." + table.tableName(),
                true,
                compressionEnabled,
                compressionMode,
                hyper.timeColumn(),
                hyper.timeType(),
                table.estimatedRows(),
                compression.totalChunks() > 0 ? compression.totalChunks() : hyper.numChunks(),
                compression.compressedChunks(),
                detailed.tableBytes(),
                detailed.indexBytes(),
                detailed.toastBytes(),
                detailed.totalBytes(),
                uncompressed,
                compressed,
                optionValue(table.reloptions(), "timescaledb.compress_segmentby", "timescaledb.segmentby"),
                optionValue(table.reloptions(), "timescaledb.compress_orderby", "timescaledb.orderby"),
                compressionPolicy,
                retentionPolicy
        );
    }

    private SizeBreakdown detailedSize(String schemaName, String tableName) {
        String relation = qualified(schemaName, tableName);
        try {
            var row = adminStorageMapper.selectDetailedSize(relation);
            return row == null ? SizeBreakdown.empty() : new SizeBreakdown(
                    valueOrZero(row.getTableBytes()),
                    valueOrZero(row.getIndexBytes()),
                    valueOrZero(row.getToastBytes()),
                    valueOrZero(row.getTotalBytes())
            );
        } catch (DataAccessException exception) {
            return SizeBreakdown.empty();
        }
    }

    private CompressionStats compressionStats(String schemaName,
                                              String tableName,
                                              TimescaleSupport support) {
        if (!support.columnstoreStats() && !support.compressionStats()) {
            return CompressionStats.empty();
        }
        String relation = qualified(schemaName, tableName);
        try {
            var row = support.columnstoreStats()
                    ? adminStorageMapper.selectColumnstoreStats(relation)
                    : adminStorageMapper.selectCompressionStats(relation);
            return row == null ? CompressionStats.empty() : new CompressionStats(
                    valueOrZero(row.getTotalChunks()),
                    valueOrZero(row.getCompressedChunks()),
                    valueOrZero(row.getBeforeBytes()),
                    valueOrZero(row.getAfterBytes())
            );
        } catch (DataAccessException exception) {
            log.debug("Unable to read compression statistics for {}.{}: {}",
                    schemaName, tableName, exception.getMostSpecificCause().getMessage());
            return CompressionStats.empty();
        }
    }

    private Map<String, HypertableMeta> loadHypertables() {
        try {
            Map<String, HypertableMeta> result = new HashMap<>();
            for (var row : adminStorageMapper.selectHypertables()) {
                result.put(tableKey(row.getHypertableSchema(), row.getHypertableName()),
                        new HypertableMeta(
                                row.getHypertableSchema(),
                                row.getHypertableName(),
                                Boolean.TRUE.equals(row.getCompressionEnabled()),
                                valueOrZero(row.getNumChunks()),
                                row.getPrimaryDimension(),
                                row.getPrimaryDimensionType()
                        )
                );
            }
            return result;
        } catch (DataAccessException exception) {
            try {
                Map<String, HypertableMeta> result = new HashMap<>();
                for (var row : adminStorageMapper.selectLegacyHypertables()) {
                    result.put(
                            tableKey(row.getHypertableSchema(), row.getHypertableName()),
                            new HypertableMeta(
                                    row.getHypertableSchema(),
                                    row.getHypertableName(),
                                    false,
                                    0,
                                    null,
                                    null
                            )
                    );
                }
                return result;
            } catch (DataAccessException ignored) {
                return Map.of();
            }
        }
    }

    private Map<String, Map<String, Object>> loadPolicies() {
        try {
            Map<String, Map<String, Object>> result = new HashMap<>();
            for (var row : adminStorageMapper.selectPolicies()) {
                String schema = row.getHypertableSchema();
                String table = row.getHypertableName();
                if (schema == null || table == null) continue;
                Map<String, Object> item = new HashMap<>();
                String proc = row.getProcName();
                String after = "policy_compression".equals(proc)
                        ? row.getCompressAfter()
                        : row.getDropAfter();
                BigDecimal scheduleSeconds = row.getScheduleSeconds();
                item.put("after", after);
                item.put("scheduleHours", scheduleSeconds == null ? null : scheduleSeconds.doubleValue() / 3600.0);
                Object nextStart = row.getNextStart();
                item.put("nextStart", nextStart == null ? null : nextStart.toString());
                String key = tableKey(schema, table);
                result.computeIfAbsent(key, ignored -> new HashMap<>())
                        .put("policy_compression".equals(proc) ? "compression" : "retention", item);
            }
            return result;
        } catch (DataAccessException exception) {
            return Map.of();
        }
    }

    private PolicyView policyView(Object value) {
        if (!(value instanceof Map<?, ?> item)) {
            return PolicyView.disabled();
        }
        String after = item.get("after") == null ? null : String.valueOf(item.get("after"));
        Object hours = item.get("scheduleHours");
        return new PolicyView(
                true,
                after,
                parseIntervalDays(after),
                hours instanceof Number number ? number.doubleValue() : null,
                item.get("nextStart") == null ? null : String.valueOf(item.get("nextStart"))
        );
    }

    private Double parseIntervalDays(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = INTERVAL_PART.matcher(value);
        double days = 0;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            double amount = Double.parseDouble(matcher.group(1));
            days += switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "year", "years" -> amount * 365;
                case "month", "months" -> amount * 30;
                case "week", "weeks" -> amount * 7;
                case "day", "days" -> amount;
                case "hour", "hours" -> amount / 24;
                default -> amount / 1440;
            };
        }
        return matched ? days : null;
    }

    private TimescaleSupport timescaleSupport() {
        String version = null;
        try {
            version = adminStorageMapper.selectTimescaleVersion();
        } catch (DataAccessException ignored) {
        }
        if (version == null) {
            return TimescaleSupport.unavailable();
        }
        return new TimescaleSupport(
                version,
                functionExists("hypertable_columnstore_stats"),
                functionExists("hypertable_compression_stats")
        );
    }

    private boolean functionExists(String name) {
        try {
            Boolean exists = adminStorageMapper.functionExists(name);
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private HypertableMeta requireHypertable(String schemaName, String tableName) throws BusinessException {
        HypertableMeta meta = loadHypertables().get(tableKey(schemaName, tableName));
        if (meta == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The selected relation is not a TimescaleDB hypertable");
        }
        return meta;
    }

    private void validatePolicyValues(boolean enabled, int afterDays, int scheduleHours) throws BusinessException {
        if (!enabled) return;
        if (afterDays < 1 || afterDays > 36_500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Policy age must be between 1 and 36500 days");
        }
        if (scheduleHours < 1 || scheduleHours > 720) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Schedule interval must be between 1 and 720 hours");
        }
    }

    private String validateSegmentBy(String schema,
                                     String table,
                                     String value,
                                     boolean required) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return "";
        if (!IDENTIFIER_LIST.matcher(normalized).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid compression segment-by columns");
        }
        validateColumns(schema, table, normalized);
        return normalized.replaceAll("\\s*,\\s*", ", ");
    }

    private String validateOrderBy(String schema,
                                   String table,
                                   String value,
                                   boolean required) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            if (required) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Compression order-by is required");
            }
            return "";
        }
        if (!ORDER_BY_LIST.matcher(normalized).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid compression order-by expression");
        }
        String columns = normalized.replaceAll("(?i)\\s+(ASC|DESC)", "")
                .replaceAll("(?i)\\s+NULLS\\s+(FIRST|LAST)", "");
        validateColumns(schema, table, columns);
        return normalized;
    }

    private void validateColumns(String schema, String table, String expression) throws BusinessException {
        String columnsOnly = expression.replaceAll("(?i)\\s+(ASC|DESC)", "")
                .replaceAll("(?i)\\s+NULLS\\s+(FIRST|LAST)", "");
        for (String column : columnsOnly.split("\\s*,\\s*")) {
            if (adminStorageMapper.countColumn(schema, table, column) == 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Unknown compression column: " + column);
            }
        }
    }

    private void validateCutoff(OffsetDateTime cutoff) throws BusinessException {
        if (cutoff == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cutoff is required");
        }
        if (cutoff.isAfter(OffsetDateTime.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cutoff cannot be in the future");
        }
    }

    private BusinessException policyFailure(DataAccessException exception) {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "TimescaleDB policy update failed: " + exception.getMostSpecificCause().getMessage()
        );
    }

    private boolean hasCompressionSettings(String reloptions) {
        return !optionValue(reloptions, "timescaledb.compress_orderby", "timescaledb.orderby").isBlank()
                || !optionValue(reloptions, "timescaledb.compress_segmentby", "timescaledb.segmentby").isBlank();
    }

    private String optionValue(String reloptions, String legacyName, String columnstoreName) {
        if (reloptions == null || reloptions.isBlank()) return "";
        for (String line : reloptions.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            String name = line.substring(0, separator).trim();
            if (name.equals(legacyName) || name.equals(columnstoreName)) {
                return line.substring(separator + 1).trim();
            }
        }
        return "";
    }

    private String qualified(String schema, String table) {
        return quoteIdentifier(schema) + "." + quoteIdentifier(table);
    }

    private String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String tableKey(String schema, String table) {
        return schema + "\u0000" + table;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private record TimescaleSupport(String version,
                                    boolean columnstoreStats,
                                    boolean compressionStats) {
        static TimescaleSupport unavailable() {
            return new TimescaleSupport(null, false, false);
        }
    }

    private record HypertableMeta(String schema,
                                  String table,
                                  boolean compressionEnabled,
                                  long numChunks,
                                  String timeColumn,
                                  String timeType) {
    }

    private record BaseTable(String schemaName,
                             String tableName,
                             long estimatedRows,
                             long heapBytes,
                             long indexBytes,
                             long totalBytes,
                             String reloptions) {
    }

    private record SizeBreakdown(long tableBytes, long indexBytes, long toastBytes, long totalBytes) {
        static SizeBreakdown empty() {
            return new SizeBreakdown(0, 0, 0, 0);
        }
    }

    private record CompressionStats(long totalChunks,
                                    long compressedChunks,
                                    long beforeCompressionBytes,
                                    long afterCompressionBytes) {
        static CompressionStats empty() {
            return new CompressionStats(0, 0, 0, 0);
        }
    }
}
