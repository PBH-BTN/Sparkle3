package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.ConfigEntry;
import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.ConfigSummary;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class DatabaseConfigService {
    public static final Set<String> HOT_RELOAD_KEYS = Set.of(
            "sparkle.ping.config.min-protocol-version",
            "sparkle.ping.config.max-protocol-version",
            "sparkle.ping.query-ip.pow-captcha",
            "sparkle.client-discovery.enabled",
            "sparkle.swarm-statistics-track.enabled",
            "sparkle.swarm-statistics-track.refresh-interval-minutes",
            "sparkle.swarm-statistics-track.window-days"
    );
    private static final String TABLE = "app_config_entries";

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private volatile boolean schemaReady;

    public DatabaseConfigService(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    public void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            createSchema();
            schemaReady = true;
        }
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_config_entries (
                    config_key VARCHAR(512) PRIMARY KEY,
                    config_value TEXT NOT NULL,
                    value_type VARCHAR(32) NOT NULL DEFAULT 'string',
                    config_group VARCHAR(128) NOT NULL DEFAULT 'other',
                    source VARCHAR(32) NOT NULL DEFAULT 'yaml',
                    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
                    hot_reload BOOLEAN NOT NULL DEFAULT FALSE,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    updated_by BIGINT
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS app_config_entries_group_idx
                ON app_config_entries (config_group)
                """);
    }

    public @NotNull List<ConfigEntry> list(String keyword, String group) {
        ensureSchema();
        String term = keyword == null ? "" : keyword.trim();
        String selectedGroup = group == null ? "" : group.trim();
        return jdbcTemplate.query("""
                SELECT config_key, config_value, value_type, config_group, source,
                       sensitive, hot_reload, updated_at, updated_by
                FROM app_config_entries
                WHERE (? = '' OR config_key ILIKE ? OR config_value ILIKE ?)
                  AND (? = '' OR config_group = ?)
                ORDER BY config_group, config_key
                """, (rs, rowNum) -> toEntry(
                rs.getString("config_key"),
                rs.getString("config_value"),
                rs.getString("value_type"),
                rs.getString("config_group"),
                rs.getString("source"),
                rs.getBoolean("sensitive"),
                rs.getBoolean("hot_reload"),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("updated_by", Long.class)
        ), term, "%" + term + "%", "%" + term + "%", selectedGroup, selectedGroup);
    }

    public @NotNull ConfigSummary summary() {
        ensureSchema();
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE source = 'yaml') AS yaml_entries,
                       COUNT(*) FILTER (WHERE source <> 'yaml') AS database_entries,
                       COUNT(*) FILTER (WHERE sensitive) AS sensitive_entries,
                       COUNT(*) FILTER (WHERE hot_reload) AS hot_entries,
                       MAX(updated_at) AS last_updated_at
                FROM app_config_entries
                """);
        List<String> groups = jdbcTemplate.queryForList(
                "SELECT DISTINCT config_group FROM app_config_entries ORDER BY config_group",
                String.class
        );
        return new ConfigSummary(
                number(row.get("total")),
                number(row.get("yaml_entries")),
                number(row.get("database_entries")),
                number(row.get("sensitive_entries")),
                number(row.get("hot_entries")),
                groups,
                row.get("last_updated_at") instanceof OffsetDateTime time ? time : null
        );
    }

    public @NotNull ConfigEntry update(String key, String value, @Nullable Long operatorId)
            throws BusinessException {
        if (key == null || key.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Config key is required");
        }
        if (value == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Config value is required");
        }
        if (isExcludedKey(key)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Datasource configuration cannot be changed here");
        }
        ensureSchema();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT config_value, value_type, sensitive, config_group, source,
                       hot_reload, updated_at, updated_by
                FROM app_config_entries
                WHERE config_key = ?
                """, key);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Config key not found");
        }
        Map<String, Object> row = rows.getFirst();
        if (Boolean.TRUE.equals(row.get("sensitive"))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Sensitive configuration is read-only");
        }
        validateValueType(String.valueOf(row.get("value_type")), value);
        jdbcTemplate.update("""
                UPDATE app_config_entries
                SET config_value = ?,
                    source = 'database',
                    updated_at = now(),
                    updated_by = ?
                WHERE config_key = ?
                """, value, operatorId, key);
        return new ConfigEntry(
                key,
                String.valueOf(row.get("config_group")),
                value,
                String.valueOf(row.get("value_type")),
                false,
                isHotReload(key),
                false,
                "database",
                OffsetDateTime.now(),
                operatorId
        );
    }

    public @NotNull Map<String, String> getRawValues(@NotNull List<String> keys) {
        if (keys.isEmpty()) return Map.of();
        ensureSchema();
        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        Map<String, String> values = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT config_key, config_value FROM app_config_entries WHERE config_key IN ("
                        + placeholders + ")",
                rs -> {
                    values.put(rs.getString("config_key"), rs.getString("config_value"));
                },
                keys.toArray()
        );
        return values;
    }

    private ConfigEntry toEntry(String key,
                                String rawValue,
                                String valueType,
                                String group,
                                String source,
                                boolean sensitive,
                                boolean hotReload,
                                OffsetDateTime updatedAt,
                                Long updatedBy) {
        boolean environmentOverride = false;
        if (!sensitive) {
            environmentOverride = hasExternalOverride(key, rawValue);
        }
        return new ConfigEntry(
                key,
                group,
                sensitive ? null : rawValue,
                valueType,
                sensitive,
                hotReload,
                environmentOverride,
                source,
                updatedAt,
                updatedBy
        );
    }

    private void validateValueType(String type, String value) throws BusinessException {
        if ("boolean".equals(type) && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Config value must be true or false");
        }
        if ("number".equals(type)) {
            try {
                new BigDecimal(value.trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Config value must be numeric");
            }
        }
    }

    public boolean isHotReload(String key) {
        return HOT_RELOAD_KEYS.contains(key);
    }

    public boolean isExcludedKey(String key) {
        return key.startsWith("spring.datasource.");
    }

    public boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("credential")
                || normalized.contains("authorization")
                || normalized.contains("private-key")
                || normalized.endsWith(".dsn");
    }

    public String groupForKey(String key) {
        String[] parts = key.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return parts[0];
    }

    public String typeForValue(String value) {
        if (value == null) return "string";
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return "boolean";
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return "number";
        }
        return "string";
    }

    public int insertIfMissing(String key, String value, String source) {
        ensureSchema();
        return jdbcTemplate.update("""
                INSERT INTO app_config_entries
                    (config_key, config_value, value_type, config_group, source, sensitive, hot_reload)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (config_key) DO NOTHING
                """,
                key,
                value,
                typeForValue(value),
                groupForKey(key),
                source,
                isSensitiveKey(key),
                isHotReload(key)
        );
    }

    public boolean exists(String key) {
        ensureSchema();
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_config_entries WHERE config_key = ?",
                Long.class,
                key
        );
        return count != null && count > 0;
    }

    public int overrideYamlValue(String key, String value) {
        ensureSchema();
        return jdbcTemplate.update("""
                UPDATE app_config_entries
                SET config_value = ?,
                    source = 'redis-migration',
                    updated_at = now()
                WHERE config_key = ?
                  AND source = 'yaml'
                """, value, key);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private boolean hasExternalOverride(String key, String databaseValue) {
        if (!(environment instanceof org.springframework.core.env.ConfigurableEnvironment configurable)) {
            String effectiveValue = environment.getProperty(key);
            return effectiveValue != null && !effectiveValue.equals(databaseValue);
        }
        Set<String> externalSources = Set.of(
                "commandLineArgs",
                "systemProperties",
                "systemEnvironment",
                "spring.application.json"
        );
        for (PropertySource<?> source : configurable.getPropertySources()) {
            if (!externalSources.contains(source.getName()) || !source.containsProperty(key)) {
                continue;
            }
            Object value = source.getProperty(key);
            return value != null && !String.valueOf(value).equals(databaseValue);
        }
        return false;
    }
}
