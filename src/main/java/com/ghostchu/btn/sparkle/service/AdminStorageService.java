package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.BanHistoryDeletePreview;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.BanHistoryDeleteResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.PolicyView;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.StorageOverview;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.TableStorageView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbcTemplate;
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

        List<BaseTable> baseTables = jdbcTemplate.query("""
                SELECT n.nspname AS schema_name,
                       c.relname AS table_name,
                       COALESCE(s.n_live_tup, 0) AS estimated_rows,
                       pg_relation_size(c.oid) AS heap_bytes,
                       pg_indexes_size(c.oid) AS index_bytes,
                       pg_total_relation_size(c.oid) AS total_bytes,
                       COALESCE(array_to_string(c.reloptions, E'\\n'), '') AS reloptions
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
                WHERE c.relkind IN ('r', 'p')
                  AND NOT c.relispartition
                  AND n.nspname NOT LIKE 'pg_%'
                  AND n.nspname <> 'information_schema'
                  AND n.nspname NOT LIKE '\\_timescaledb\\_%' ESCAPE '\\'
                ORDER BY pg_total_relation_size(c.oid) DESC, n.nspname, c.relname
                """, (rs, rowNum) -> new BaseTable(
                rs.getString("schema_name"),
                rs.getString("table_name"),
                rs.getLong("estimated_rows"),
                rs.getLong("heap_bytes"),
                rs.getLong("index_bytes"),
                rs.getLong("total_bytes"),
                rs.getString("reloptions")
        ));

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

        try {
            if (support.columnstoreStats()) {
                List<String> options = new ArrayList<>();
                options.add("timescaledb.enable_columnstore = true");
                if (!safeSegmentBy.isBlank()) {
                    options.add("timescaledb.segmentby = '" + sqlLiteral(safeSegmentBy) + "'");
                }
                options.add("timescaledb.orderby = '" + sqlLiteral(safeOrderBy) + "'");
                jdbcTemplate.execute("ALTER TABLE " + relation + " SET (" + String.join(", ", options) + ")");
                jdbcTemplate.update("CALL remove_columnstore_policy(?::regclass, if_exists => true)", relation);
                if (enabled) {
                    jdbcTemplate.update("""
                            CALL add_columnstore_policy(
                                ?::regclass,
                                after => make_interval(days => ?),
                                schedule_interval => make_interval(hours => ?),
                                if_not_exists => false
                            )
                            """, relation, compressAfterDays, scheduleHours);
                }
            } else {
                List<String> options = new ArrayList<>();
                options.add("timescaledb.compress = true");
                if (!safeSegmentBy.isBlank()) {
                    options.add("timescaledb.compress_segmentby = '" + sqlLiteral(safeSegmentBy) + "'");
                }
                options.add("timescaledb.compress_orderby = '" + sqlLiteral(safeOrderBy) + "'");
                jdbcTemplate.execute("ALTER TABLE " + relation + " SET (" + String.join(", ", options) + ")");
                jdbcTemplate.queryForList(
                        "SELECT remove_compression_policy(?::regclass, if_exists => true)",
                        relation
                );
                if (enabled) {
                    jdbcTemplate.queryForList("""
                            SELECT add_compression_policy(
                                ?::regclass,
                                compress_after => make_interval(days => ?),
                                schedule_interval => make_interval(hours => ?),
                                if_not_exists => false
                            )
                    """, relation, compressAfterDays, scheduleHours);
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
            jdbcTemplate.queryForList(
                    "SELECT remove_retention_policy(?::regclass, if_exists => true)",
                    relation
            );
            if (enabled) {
                jdbcTemplate.queryForList("""
                        SELECT add_retention_policy(
                            ?::regclass,
                            drop_after => make_interval(days => ?),
                            schedule_interval => make_interval(hours => ?),
                            if_not_exists => false
                        )
                """, relation, dropAfterDays, scheduleHours);
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
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + qualified(BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE)
                            + " WHERE populate_time < ?",
                    Long.class,
                    cutoff
            );
            return new BanHistoryDeletePreview(cutoff, false, 0, 0, count, null, null);
        }
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) AS chunk_count,
                           COUNT(*) FILTER (WHERE is_compressed) AS compressed_count,
                           MIN(range_start) AS oldest_start,
                           MAX(range_end) AS newest_end
                    FROM timescaledb_information.chunks
                    WHERE hypertable_schema = ?
                      AND hypertable_name = ?
                      AND range_end <= ?
                    """, (rs, rowNum) -> new BanHistoryDeletePreview(
                    cutoff,
                    true,
                    rs.getLong("chunk_count"),
                    rs.getLong("compressed_count"),
                    null,
                    rs.getObject("oldest_start", OffsetDateTime.class),
                    rs.getObject("newest_end", OffsetDateTime.class)
            ), BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE, cutoff);
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
            List<String> dropped = jdbcTemplate.queryForList(
                    "SELECT drop_chunks(?::regclass, older_than => ?::timestamptz)::text",
                    String.class,
                    qualified(BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE),
                    cutoff
            );
            affected = dropped.size();
            jdbcTemplate.execute("ANALYZE " + qualified(BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE));
        } else {
            affected = jdbcTemplate.update(
                    "DELETE FROM " + qualified(BAN_HISTORY_SCHEMA, BAN_HISTORY_TABLE)
                            + " WHERE populate_time < ?",
                    cutoff
            );
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
            SizeBreakdown result = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(table_bytes), 0) AS table_bytes,
                           COALESCE(SUM(index_bytes), 0) AS index_bytes,
                           COALESCE(SUM(toast_bytes), 0) AS toast_bytes,
                           COALESCE(SUM(total_bytes), 0) AS total_bytes
                    FROM hypertable_detailed_size(?::regclass)
                    """, (rs, rowNum) -> new SizeBreakdown(
                    rs.getLong("table_bytes"),
                    rs.getLong("index_bytes"),
                    rs.getLong("toast_bytes"),
                    rs.getLong("total_bytes")
            ), relation);
            return result == null ? SizeBreakdown.empty() : result;
        } catch (DataAccessException exception) {
            return SizeBreakdown.empty();
        }
    }

    private CompressionStats compressionStats(String schemaName,
                                              String tableName,
                                              TimescaleSupport support) {
        String function = support.columnstoreStats()
                ? "hypertable_columnstore_stats"
                : support.compressionStats() ? "hypertable_compression_stats" : null;
        if (function == null) {
            return CompressionStats.empty();
        }
        String relation = qualified(schemaName, tableName);
        try {
            CompressionStats stats = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(total_chunks), 0) AS total_chunks,
                           COALESCE(SUM(number_compressed_chunks), 0) AS compressed_chunks,
                           COALESCE(SUM(before_compression_total_bytes), 0) AS before_bytes,
                           COALESCE(SUM(after_compression_total_bytes), 0) AS after_bytes
                    FROM %s(?::regclass)
                    """.formatted(function), (rs, rowNum) -> new CompressionStats(
                    rs.getLong("total_chunks"),
                    rs.getLong("compressed_chunks"),
                    rs.getLong("before_bytes"),
                    rs.getLong("after_bytes")
            ), relation);
            return stats == null ? CompressionStats.empty() : stats;
        } catch (DataAccessException exception) {
            log.debug("Unable to read compression statistics for {}.{}: {}",
                    schemaName, tableName, exception.getMostSpecificCause().getMessage());
            return CompressionStats.empty();
        }
    }

    private Map<String, HypertableMeta> loadHypertables() {
        try {
            Map<String, HypertableMeta> result = new HashMap<>();
            jdbcTemplate.query("""
                    SELECT hypertable_schema,
                           hypertable_name,
                           compression_enabled,
                           num_chunks,
                           primary_dimension,
                           primary_dimension_type::text AS primary_dimension_type
                    FROM timescaledb_information.hypertables
                    """, rs -> {
                result.put(tableKey(rs.getString("hypertable_schema"), rs.getString("hypertable_name")),
                        new HypertableMeta(
                                rs.getString("hypertable_schema"),
                                rs.getString("hypertable_name"),
                                rs.getBoolean("compression_enabled"),
                                rs.getLong("num_chunks"),
                                rs.getString("primary_dimension"),
                                rs.getString("primary_dimension_type")
                        ));
            });
            return result;
        } catch (DataAccessException exception) {
            try {
                Map<String, HypertableMeta> result = new HashMap<>();
                jdbcTemplate.query("""
                        SELECT hypertable_schema, hypertable_name
                        FROM timescaledb_information.hypertables
                        """, rs -> {
                    result.put(
                            tableKey(rs.getString("hypertable_schema"), rs.getString("hypertable_name")),
                            new HypertableMeta(
                                    rs.getString("hypertable_schema"),
                                    rs.getString("hypertable_name"),
                                    false,
                                    0,
                                    null,
                                    null
                            )
                    );
                });
                return result;
            } catch (DataAccessException ignored) {
                return Map.of();
            }
        }
    }

    private Map<String, Map<String, Object>> loadPolicies() {
        try {
            Map<String, Map<String, Object>> result = new HashMap<>();
            jdbcTemplate.query("""
                    SELECT hypertable_schema,
                           hypertable_name,
                           proc_name,
                           config->>'compress_after' AS compress_after,
                           config->>'drop_after' AS drop_after,
                           EXTRACT(EPOCH FROM schedule_interval)::double precision AS schedule_seconds,
                           next_start
                    FROM timescaledb_information.jobs
                    WHERE proc_name IN ('policy_compression', 'policy_retention')
                    """, rs -> {
                String schema = rs.getString("hypertable_schema");
                String table = rs.getString("hypertable_name");
                if (schema == null || table == null) return;
                Map<String, Object> item = new HashMap<>();
                String proc = rs.getString("proc_name");
                String after = "policy_compression".equals(proc)
                        ? rs.getString("compress_after")
                        : rs.getString("drop_after");
                BigDecimal scheduleSeconds = rs.getBigDecimal("schedule_seconds");
                item.put("after", after);
                item.put("scheduleHours", scheduleSeconds == null ? null : scheduleSeconds.doubleValue() / 3600.0);
                Object nextStart = rs.getObject("next_start");
                item.put("nextStart", nextStart == null ? null : nextStart.toString());
                String key = tableKey(schema, table);
                result.computeIfAbsent(key, ignored -> new HashMap<>())
                        .put("policy_compression".equals(proc) ? "compression" : "retention", item);
            });
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
            version = jdbcTemplate.queryForObject(
                    "SELECT extversion FROM pg_extension WHERE extname = 'timescaledb'",
                    String.class
            );
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
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT to_regprocedure(? || '(regclass)') IS NOT NULL",
                    Boolean.class,
                    name
            );
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
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = ? AND column_name = ?
                    """, Long.class, schema, table, column);
            if (count == null || count == 0) {
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

    private String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private String tableKey(String schema, String table) {
        return schema + "\u0000" + table;
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
