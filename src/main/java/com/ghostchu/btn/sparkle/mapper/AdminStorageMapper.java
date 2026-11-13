package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.AdminStorageRows.BaseTableRow;
import com.ghostchu.btn.sparkle.service.dto.AdminStorageRows.ChunkSummaryRow;
import com.ghostchu.btn.sparkle.service.dto.AdminStorageRows.CompressionStatsRow;
import com.ghostchu.btn.sparkle.service.dto.AdminStorageRows.HypertableRow;
import com.ghostchu.btn.sparkle.service.dto.AdminStorageRows.PolicyRow;
import com.ghostchu.btn.sparkle.service.dto.AdminStorageRows.SizeRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PostgreSQL catalog and TimescaleDB administration queries.
 *
 * <p>PostgreSQL does not permit table names or storage parameter names to be
 * bound as JDBC parameters. Dynamic fragments are therefore supplied through
 * the small value types below, which quote identifiers and reject any
 * expression outside the storage-option grammar used by this service.</p>
 */
public interface AdminStorageMapper {
    Pattern STORAGE_OPTION = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*(?:\\s+(?:ASC|DESC))?"
                    + "(?:\\s+NULLS\\s+(?:FIRST|LAST))?"
                    + "(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*(?:\\s+(?:ASC|DESC))?"
                    + "(?:\\s+NULLS\\s+(?:FIRST|LAST))?)*$",
            Pattern.CASE_INSENSITIVE
    );

    @Select("""
            SELECT n.nspname AS "schemaName",
                   c.relname AS "tableName",
                   COALESCE(s.n_live_tup, 0) AS "estimatedRows",
                   pg_relation_size(c.oid) AS "heapBytes",
                   pg_indexes_size(c.oid) AS "indexBytes",
                   pg_total_relation_size(c.oid) AS "totalBytes",
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
            """)
    List<BaseTableRow> selectBaseTables();

    @Select("""
            SELECT hypertable_schema AS "hypertableSchema",
                   hypertable_name AS "hypertableName",
                   compression_enabled AS "compressionEnabled",
                   num_chunks AS "numChunks",
                   primary_dimension AS "primaryDimension",
                   primary_dimension_type::text AS "primaryDimensionType"
            FROM timescaledb_information.hypertables
            """)
    List<HypertableRow> selectHypertables();

    @Select("""
            SELECT hypertable_schema AS "hypertableSchema",
                   hypertable_name AS "hypertableName"
            FROM timescaledb_information.hypertables
            """)
    List<HypertableRow> selectLegacyHypertables();

    @Select("""
            SELECT hypertable_schema AS "hypertableSchema",
                   hypertable_name AS "hypertableName",
                   proc_name AS "procName",
                   config->>'compress_after' AS "compressAfter",
                   config->>'drop_after' AS "dropAfter",
                   EXTRACT(EPOCH FROM schedule_interval)::double precision AS "scheduleSeconds",
                   next_start AS "nextStart"
            FROM timescaledb_information.jobs
            WHERE proc_name IN ('policy_compression', 'policy_retention')
            """)
    List<PolicyRow> selectPolicies();

    @Update("CALL remove_columnstore_policy(#{relation}::regclass, if_exists => true)")
    void removeColumnstorePolicy(@Param("relation") String relation);

    @Update("""
            CALL add_columnstore_policy(
                #{relation}::regclass,
                after => make_interval(days => #{afterDays}),
                schedule_interval => make_interval(hours => #{scheduleHours}),
                if_not_exists => false
            )
            """)
    void addColumnstorePolicy(
            @Param("relation") String relation,
            @Param("afterDays") int afterDays,
            @Param("scheduleHours") int scheduleHours
    );

    @Update("""
            ALTER TABLE ${relation.qualified}
            SET (
                timescaledb.enable_columnstore = true,
                timescaledb.orderby = '${orderBy.value}'
            )
            """)
    void setColumnstoreOptionsWithoutSegment(
            @Param("relation") SqlRelation relation,
            @Param("orderBy") SqlOptionValue orderBy
    );

    @Update("""
            ALTER TABLE ${relation.qualified}
            SET (
                timescaledb.enable_columnstore = true,
                timescaledb.segmentby = '${segmentBy.value}',
                timescaledb.orderby = '${orderBy.value}'
            )
            """)
    void setColumnstoreOptionsWithSegment(
            @Param("relation") SqlRelation relation,
            @Param("segmentBy") SqlOptionValue segmentBy,
            @Param("orderBy") SqlOptionValue orderBy
    );

    @Select("SELECT remove_compression_policy(#{relation}::regclass, if_exists => true)")
    boolean removeCompressionPolicy(@Param("relation") String relation);

    @Select("""
            SELECT add_compression_policy(
                #{relation}::regclass,
                compress_after => make_interval(days => #{afterDays}),
                schedule_interval => make_interval(hours => #{scheduleHours}),
                if_not_exists => false
            )
            """)
    int addCompressionPolicy(
            @Param("relation") String relation,
            @Param("afterDays") int afterDays,
            @Param("scheduleHours") int scheduleHours
    );

    @Update("""
            ALTER TABLE ${relation.qualified}
            SET (
                timescaledb.compress = true,
                timescaledb.compress_orderby = '${orderBy.value}'
            )
            """)
    void setLegacyCompressionOptionsWithoutSegment(
            @Param("relation") SqlRelation relation,
            @Param("orderBy") SqlOptionValue orderBy
    );

    @Update("""
            ALTER TABLE ${relation.qualified}
            SET (
                timescaledb.compress = true,
                timescaledb.compress_segmentby = '${segmentBy.value}',
                timescaledb.compress_orderby = '${orderBy.value}'
            )
            """)
    void setLegacyCompressionOptionsWithSegment(
            @Param("relation") SqlRelation relation,
            @Param("segmentBy") SqlOptionValue segmentBy,
            @Param("orderBy") SqlOptionValue orderBy
    );

    @Select("SELECT remove_retention_policy(#{relation}::regclass, if_exists => true)")
    boolean removeRetentionPolicy(@Param("relation") String relation);

    @Select("""
            SELECT add_retention_policy(
                #{relation}::regclass,
                drop_after => make_interval(days => #{afterDays}),
                schedule_interval => make_interval(hours => #{scheduleHours}),
                if_not_exists => false
            )
            """)
    int addRetentionPolicy(
            @Param("relation") String relation,
            @Param("afterDays") int afterDays,
            @Param("scheduleHours") int scheduleHours
    );

    @Select("""
            SELECT COALESCE(SUM(table_bytes), 0) AS "tableBytes",
                   COALESCE(SUM(index_bytes), 0) AS "indexBytes",
                   COALESCE(SUM(toast_bytes), 0) AS "toastBytes",
                   COALESCE(SUM(total_bytes), 0) AS "totalBytes"
            FROM hypertable_detailed_size(#{relation}::regclass)
            """)
    SizeRow selectDetailedSize(@Param("relation") String relation);

    @Select("""
            SELECT COALESCE(SUM(total_chunks), 0) AS "totalChunks",
                   COALESCE(SUM(number_compressed_chunks), 0) AS "compressedChunks",
                   COALESCE(SUM(before_compression_total_bytes), 0) AS "beforeBytes",
                   COALESCE(SUM(after_compression_total_bytes), 0) AS "afterBytes"
            FROM hypertable_columnstore_stats(#{relation}::regclass)
            """)
    CompressionStatsRow selectColumnstoreStats(@Param("relation") String relation);

    @Select("""
            SELECT COALESCE(SUM(total_chunks), 0) AS "totalChunks",
                   COALESCE(SUM(number_compressed_chunks), 0) AS "compressedChunks",
                   COALESCE(SUM(before_compression_total_bytes), 0) AS "beforeBytes",
                   COALESCE(SUM(after_compression_total_bytes), 0) AS "afterBytes"
            FROM hypertable_compression_stats(#{relation}::regclass)
            """)
    CompressionStatsRow selectCompressionStats(@Param("relation") String relation);

    @Select("SELECT extversion FROM pg_extension WHERE extname = 'timescaledb'")
    String selectTimescaleVersion();

    @Select("SELECT to_regprocedure(#{name} || '(regclass)') IS NOT NULL")
    Boolean functionExists(@Param("name") String name);

    @Select("""
            SELECT COUNT(*) AS "chunkCount",
                   COUNT(*) FILTER (WHERE is_compressed) AS "compressedCount",
                   MIN(range_start) AS "oldestStart",
                   MAX(range_end) AS "newestEnd"
            FROM timescaledb_information.chunks
            WHERE hypertable_schema = #{schema}
              AND hypertable_name = #{table}
              AND range_end <= #{cutoff}
            """)
    ChunkSummaryRow selectChunkSummary(
            @Param("schema") String schema,
            @Param("table") String table,
            @Param("cutoff") OffsetDateTime cutoff
    );

    @Select("SELECT COUNT(*) FROM public.ban_history WHERE populate_time < #{cutoff}")
    Long countBanHistoryBefore(@Param("cutoff") OffsetDateTime cutoff);

    @Select("""
            SELECT drop_chunks(
                #{relation}::regclass,
                older_than => #{cutoff}::timestamptz
            )::text
            """)
    List<String> dropBanHistoryChunks(
            @Param("relation") String relation,
            @Param("cutoff") OffsetDateTime cutoff
    );

    @Update("ANALYZE public.ban_history")
    void analyzeBanHistory();

    @Update("DELETE FROM public.ban_history WHERE populate_time < #{cutoff}")
    int deleteBanHistoryBefore(@Param("cutoff") OffsetDateTime cutoff);

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = #{schema}
              AND table_name = #{table}
              AND column_name = #{column}
            """)
    long countColumn(
            @Param("schema") String schema,
            @Param("table") String table,
            @Param("column") String column
    );

    record SqlRelation(String schema, String table) {
        public SqlRelation {
            schema = requireIdentifier(schema, "schema");
            table = requireIdentifier(table, "table");
        }

        public String getQualified() {
            return quote(schema) + "." + quote(table);
        }

        private static String requireIdentifier(String value, String label) {
            if (value == null || value.isBlank() || value.indexOf('\0') >= 0 || value.length() > 63) {
                throw new IllegalArgumentException("Invalid PostgreSQL " + label + " identifier");
            }
            return value;
        }

        private static String quote(String value) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }

    record SqlOptionValue(String value) {
        public SqlOptionValue {
            if (value == null || !STORAGE_OPTION.matcher(value.trim()).matches()) {
                throw new IllegalArgumentException("Invalid TimescaleDB storage option");
            }
            value = value.trim();
        }

        @Override
        public String toString() {
            return value;
        }

        public String getValue() {
            return value;
        }
    }
}
