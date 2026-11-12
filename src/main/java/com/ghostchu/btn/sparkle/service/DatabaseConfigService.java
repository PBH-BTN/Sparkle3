package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.ConfigEntry;
import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.ConfigSummary;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.entity.AppConfigEntry;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AppConfigEntryMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpStatus;
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
            "sparkle.swarm-statistics-track.window-days",
            "sparkle.homepage-statistics-refresh.modules.ban-history.enabled",
            "sparkle.homepage-statistics-refresh.modules.ban-history.refresh-interval-minutes",
            "sparkle.homepage-statistics-refresh.modules.active-apps.enabled",
            "sparkle.homepage-statistics-refresh.modules.active-apps.refresh-interval-minutes",
            "sparkle.homepage-statistics-refresh.modules.swarm-tracker.enabled",
            "sparkle.homepage-statistics-refresh.modules.swarm-tracker.refresh-interval-minutes",
            "sparkle.homepage-statistics-refresh.modules.torrents.enabled",
            "sparkle.homepage-statistics-refresh.modules.torrents.refresh-interval-minutes",
            "sparkle.homepage-statistics-refresh.modules.network-charts.enabled",
            "sparkle.homepage-statistics-refresh.modules.network-charts.refresh-interval-minutes",
            "sparkle.homepage-statistics-refresh.modules.companion-charts.enabled",
            "sparkle.homepage-statistics-refresh.modules.companion-charts.refresh-interval-minutes",
            "sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.enabled",
            "sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.refresh-interval-minutes",
            "sparkle.analyse.geoip-city-blacklist.enabled",
            "sparkle.analyse.geoip-city-blacklist.schedule",
            "sparkle.analyse.mass-download-monitor.enabled",
            "sparkle.analyse.mass-download-monitor.schedule",
            "sparkle.analyse.mass-download-monitor.duration",
            "sparkle.analyse.mass-download-monitor.ipv4-prefix",
            "sparkle.analyse.mass-download-monitor.ipv6-prefix",
            "sparkle.analyse.mass-download-monitor.threshold-download",
            "sparkle.user-contact-email.subject-template",
            "sparkle.user-contact-email.content-template",
            "sparkle.footer.config"
    );
    private static final String TABLE = "app_config_entries";

    private final AppConfigEntryMapper configMapper;
    private final Environment environment;

    public DatabaseConfigService(AppConfigEntryMapper configMapper, Environment environment) {
        this.configMapper = configMapper;
        this.environment = environment;
    }

    public @NotNull List<ConfigEntry> list(String keyword, String group) {
        String term = keyword == null ? "" : keyword.trim();
        String selectedGroup = group == null ? "" : group.trim();
        QueryWrapper<AppConfigEntry> wrapper = new QueryWrapper<>();
        if (!term.isBlank()) {
            wrapper.and(query -> query
                    .like("config_key", term)
                    .or()
                    .like("config_value", term));
        }
        if (!selectedGroup.isBlank()) {
            wrapper.eq("config_group", selectedGroup);
        }
        wrapper.orderByAsc("config_group").orderByAsc("config_key");
        return configMapper.selectList(wrapper).stream()
                .map(this::toEntry)
                .toList();
    }

    public @NotNull ConfigSummary summary() {
        List<AppConfigEntry> entries = configMapper.selectList(null);
        long yamlEntries = entries.stream().filter(item -> "yaml".equals(item.getSource())).count();
        long sensitiveEntries = entries.stream().filter(item -> Boolean.TRUE.equals(item.getSensitive())).count();
        long hotEntries = entries.stream().filter(item -> Boolean.TRUE.equals(item.getHotReload())).count();
        List<String> groups = entries.stream().map(AppConfigEntry::getConfigGroup)
                .distinct().sorted().toList();
        OffsetDateTime lastUpdatedAt = entries.stream()
                .map(AppConfigEntry::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        return new ConfigSummary(
                entries.size(),
                yamlEntries,
                entries.size() - yamlEntries,
                sensitiveEntries,
                hotEntries,
                groups,
                lastUpdatedAt
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
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Datasource and schema migration configuration cannot be changed here");
        }
        AppConfigEntry row = configMapper.selectById(key);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Config key not found");
        }
        if (Boolean.TRUE.equals(row.getSensitive())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Sensitive configuration is read-only");
        }
        validateValueType(row.getValueType(), value);
        row.setConfigValue(value)
                .setSource("database")
                .setUpdatedAt(OffsetDateTime.now())
                .setUpdatedBy(operatorId);
        configMapper.updateById(row);
        return new ConfigEntry(
                key,
                row.getConfigGroup(),
                value,
                row.getValueType(),
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
        Map<String, String> values = new LinkedHashMap<>();
        configMapper.selectBatchIds(keys).forEach(entry ->
                values.put(entry.getConfigKey(), entry.getConfigValue()));
        return values;
    }

    public void updateSensitive(String key, String value, @Nullable Long operatorId)
            throws BusinessException {
        if (key == null || key.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Config key is required");
        }
        if (value == null || value.length() > 4_096) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Sensitive config value is invalid");
        }
        if (isExcludedKey(key)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Datasource and schema migration configuration cannot be changed here");
        }
        AppConfigEntry row = configMapper.selectById(key);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Config key not found");
        }
        if (!Boolean.TRUE.equals(row.getSensitive())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Config key is not marked as sensitive");
        }
        row.setConfigValue(value)
                .setSource("database")
                .setUpdatedAt(OffsetDateTime.now())
                .setUpdatedBy(operatorId);
        configMapper.updateById(row);
    }

    private ConfigEntry toEntry(AppConfigEntry entry) {
        String key = entry.getConfigKey();
        String rawValue = entry.getConfigValue();
        boolean sensitive = Boolean.TRUE.equals(entry.getSensitive());
        boolean hotReload = Boolean.TRUE.equals(entry.getHotReload());
        boolean environmentOverride = false;
        if (!sensitive) {
            environmentOverride = hasExternalOverride(key, rawValue);
        }
        return new ConfigEntry(
                key,
                entry.getConfigGroup(),
                sensitive ? null : rawValue,
                entry.getValueType(),
                sensitive,
                hotReload,
                environmentOverride,
                entry.getSource(),
                entry.getUpdatedAt(),
                entry.getUpdatedBy()
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
        return HOT_RELOAD_KEYS.contains(key)
                || key.startsWith("sparkle.ping.")
                || "sparkle.root-url".equals(key)
                || "sparkle.chn-root-url".equals(key)
                || key.startsWith("sparkle.captcha.pow.")
                || key.startsWith("sparkle.query.query-ip.")
                || key.startsWith("sparkle.ranking.weight.")
                || key.startsWith("sparkle.admin.user-ip-warning.")
                || key.startsWith("sparkle.measure.")
                || key.startsWith("sparkle.github-sync.")
                || key.startsWith("sparkle.github-community.")
                || key.startsWith("sparkle.afdian.")
                || key.startsWith("sparkle.memberships.")
                || key.startsWith("sparkle.security.")
                || key.startsWith("sparkle.userapp.auto-account.")
                || key.startsWith("sparkle.banhistory.")
                || key.startsWith("sparkle.points.")
                || key.startsWith("sparkle.version-alert.")
                || key.startsWith("sparkle.user-contact-email.")
                || key.startsWith("sparkle.analyse.")
                || key.startsWith("sparkle.user-avatar.");
    }

    public boolean isExcludedKey(String key) {
        return !isManagedKey(key);
    }

    public static boolean isManagedKey(String key) {
        return key != null && key.startsWith("sparkle.");
    }

    public boolean isDeprecatedKey(String key) {
        return key.startsWith("sparkle.machine-learning.")
                || key.startsWith("sparkle.tracker.")
                || key.startsWith("sparkle.statistics.")
                || "sparkle.analyse.overdownload-analyse.threshold".equals(key)
                || "sparkle.ranking.database-type".equals(key)
                || "sparkle.swarm-statistics-track.cron".equals(key)
                || "sparkle.swarm-statistics-track.duration".equals(key);
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
        if (configMapper.selectById(key) != null) return 0;
        return configMapper.insert(new AppConfigEntry()
                .setConfigKey(key)
                .setConfigValue(value)
                .setValueType(typeForValue(value))
                .setConfigGroup(groupForKey(key))
                .setSource(source)
                .setSensitive(isSensitiveKey(key))
                .setHotReload(isHotReload(key))
                .setUpdatedAt(OffsetDateTime.now()));
    }

    public int upsertYamlValue(String key, String value) {
        AppConfigEntry existing = configMapper.selectById(key);
        if (existing == null) {
            return insertIfMissing(key, value, "yaml");
        }
        if (!Set.of("yaml", "sql-default").contains(existing.getSource())) {
            return 0;
        }
        String type = typeForValue(value);
        String group = groupForKey(key);
        boolean sensitive = isSensitiveKey(key);
        boolean hotReload = isHotReload(key);
        if (java.util.Objects.equals(existing.getConfigValue(), value)
                && java.util.Objects.equals(existing.getValueType(), type)
                && java.util.Objects.equals(existing.getConfigGroup(), group)
                && java.util.Objects.equals(existing.getSensitive(), sensitive)
                && java.util.Objects.equals(existing.getHotReload(), hotReload)) {
            return 0;
        }
        existing.setConfigValue(value)
                .setValueType(type)
                .setConfigGroup(group)
                .setSensitive(sensitive)
                .setHotReload(hotReload)
                .setUpdatedAt(OffsetDateTime.now());
        return configMapper.updateById(existing);
    }

    public boolean exists(String key) {
        return configMapper.selectById(key) != null;
    }

    public int overrideYamlValue(String key, String value) {
        AppConfigEntry entry = configMapper.selectById(key);
        if (entry == null || !Set.of("yaml", "sql-default").contains(entry.getSource())) {
            return 0;
        }
        entry.setConfigValue(value)
                .setSource("redis-migration")
                .setUpdatedAt(OffsetDateTime.now());
        return configMapper.updateById(entry);
    }

    public boolean hasExternalOverride(String key, String databaseValue) {
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
