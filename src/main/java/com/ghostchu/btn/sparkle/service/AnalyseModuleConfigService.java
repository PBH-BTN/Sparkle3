package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnalyseModuleModels.ModuleFieldView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnalyseModuleModels.ModuleView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class AnalyseModuleConfigService {
    private static final String PREFIX = "sparkle.analyse.";
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final long HOUR_MILLIS = 3_600_000L;
    private static final long GIB_BYTES = 1_073_741_824L;
    private static final List<ModuleDefinition> MODULES = List.of(
            module("untrusted-vote", "untrusted-vote",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 0 * * * *"),
                    durationHours("duration", "duration", 1_209_600_000L),
                    text("include-modules", "include-modules",
                            "com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker"),
                    bool("ipv4-enable", "ipv4.enable", true),
                    integer("ipv4-min-userapps-vote", "ipv4.min-userapps-vote", 3),
                    integer("ipv4-min-ban-count", "ipv4.min-ban-count", 5),
                    bool("ipv6-enable", "ipv6.enable", true),
                    integer("ipv6-min-userapps-vote", "ipv6.min-userapps-vote", 3),
                    integer("ipv6-min-ban-count", "ipv6.min-ban-count", 5)
            ),
            module("overdownload-analyse", "overdownload-analyse",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 0 * * * *"),
                    durationHours("duration", "duration", 604_800_000L),
                    decimal("threshold-ratio", "threshold-ratio", "3.2"),
                    bytesGib("threshold-traffic", "threshold-traffic", 15_000_000_000L),
                    bool("use-materialized-view", "use-materialized-view", false),
                    bool("materialized-view-refresh", "materialized-view-refresh", true)
            ),
            module("concurrent-download-analyse", "concurrent-download-analyse",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 0 * * * *"),
                    durationHours("duration", "duration", 3_600_000L),
                    decimal("threshold-concurrent", "threshold-concurrent", "150"),
                    decimal("threshold-userapps", "threshold-userapps", "3")
            ),
            module("mass-download-monitor", "mass-download-monitor",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 0 * * * *"),
                    durationHours("duration", "duration", 86_400_000L),
                    integer("ipv4-prefix", "ipv4-prefix", 32),
                    integer("ipv6-prefix", "ipv6-prefix", 56),
                    bytesGb("threshold-download", "threshold-download", 3_072L)
            ),
            simpleIdentityModule("random-identity-analyse"),
            simpleIdentityModule("rain000-identity-analyse"),
            simpleIdentityModule("gopeeddev-identity-analyse"),
            simpleIdentityModule("datacenter-highrisk-analyse"),
            module("geoip-city-blacklist", "geoip-city-blacklist",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 0 4 * * *")
            ),
            module("btnbypass-userapps", "btnbypass-userapps",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 */20 * * * *")
            ),
            module("points-dynamic", "points-dynamic",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 5 * * * *")
            ),
            module("swarm-peer-distribution", "swarm-peer-distribution",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 10 * * * *")
            ),
            module("ban-peer-distribution", "ban-peer-distribution",
                    bool("enabled", "enabled", true),
                    cron("schedule", "schedule", "0 20 2 * * *")
            ),
            module("points-settings", "points-settings",
                    bool("login-enabled", "login-enabled", true),
                    bool("active-app-enabled", "active-app-enabled", true),
                    bool("app-online-enabled", "app-online-enabled", true),
                    bool("ban-history-enabled", "ban-history-enabled", true),
                    bool("swarm-tracker-enabled", "swarm-tracker-enabled", true),
                    bool("rules-fetch-enabled", "rules-fetch-enabled", true),
                    bool("keepalive-enabled", "keepalive-enabled", true),
                    bool("rule-hit-ip-enabled", "rule-hit-ip-enabled", true),
                    bool("new-ip-enabled", "new-ip-enabled", true),
                    bool("wasted-upload-enabled", "wasted-upload-enabled", true),
                    bool("new-torrent-enabled", "new-torrent-enabled", true),
                    bool("client-discovery-enabled", "client-discovery-enabled", true),
                    bool("dynamic-seed-upload-enabled", "dynamic-seed-upload-enabled", true),
                    bool("dynamic-download-enabled", "dynamic-download-enabled", true),
                    bool("dynamic-hot-peer-enabled", "dynamic-hot-peer-enabled", true)
            ),
            module("points-fixed-rules", "points-fixed-rules",
                    longField("login-points", "login-points", 1000L),
                    longField("login-daily-cap", "login-daily-cap", 1000L),
                    longField("active-app-points", "active-app-points", 5000L),
                    longField("app-online-points", "app-online-points", 500L),
                    integer("app-online-gap-minutes", "app-online-gap-minutes", 15),
                    longField("ban-history-points", "ban-history-points", 3L),
                    longField("ban-history-daily-cap", "ban-history-daily-cap", 2000L),
                    longField("swarm-tracker-points", "swarm-tracker-points", 3L),
                    longField("swarm-tracker-daily-cap", "swarm-tracker-daily-cap", 2000L),
                    longField("rules-fetch-points", "rules-fetch-points", 2L),
                    longField("rules-fetch-daily-cap", "rules-fetch-daily-cap", 2000L),
                    longField("keepalive-points", "keepalive-points", 1L),
                    longField("keepalive-daily-cap", "keepalive-daily-cap", 2000L),
                    longField("rule-hit-ip-points", "rule-hit-ip-points", 10L),
                    longField("rule-hit-ip-daily-cap", "rule-hit-ip-daily-cap", 2000L),
                    longField("new-ip-points", "new-ip-points", 20L),
                    longField("new-ip-daily-cap", "new-ip-daily-cap", 2000L),
                    longField("wasted-upload-points-per-mib", "wasted-upload-points-per-mib", 10L),
                    longField("wasted-upload-mib", "wasted-upload-mib", 1L),
                    longField("wasted-upload-daily-cap", "wasted-upload-daily-cap", 3000L)
            ),
            module("points-once-rules", "points-once-rules",
                    longField("new-torrent-points", "new-torrent-points", 500L),
                    longField("client-discovery-points", "client-discovery-points", 4L)
            ),
            module("points-dynamic-rules", "points-dynamic-rules",
                    longField("dynamic-seed-upload-points", "dynamic-seed-upload-points", 180L),
                    longField("dynamic-seed-upload-mib", "dynamic-seed-upload-mib", 200L),
                    longField("dynamic-download-points", "dynamic-download-points", 50L),
                    longField("dynamic-download-mib", "dynamic-download-mib", 512L),
                    longField("dynamic-hot-peer-points", "dynamic-hot-peer-points", 100L),
                    integer("dynamic-hot-peer-top", "dynamic-hot-peer-top", 50),
                    longField("dynamic-torrent-size-gib", "dynamic-torrent-size-gib", 1L)
            )
    );
    private static final Map<String, ModuleDefinition> MODULE_INDEX = MODULES.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(ModuleDefinition::id, value -> value));
    private static final Map<String, List<RedisKeyConstant>> OUTPUT_KEYS = Map.of(
            "untrusted-vote", List.of(
                    RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE,
                    RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VERSION
            ),
            "overdownload-analyse", List.of(
                    RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE,
                    RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION
            ),
            "concurrent-download-analyse", List.of(
                    RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VALUE,
                    RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION
            ),
            "mass-download-monitor", List.of(
                    RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VALUE,
                    RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VERSION
            ),
            "random-identity-analyse", List.of(
                    RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VALUE,
                    RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VERSION
            ),
            "rain000-identity-analyse", List.of(
                    RedisKeyConstant.ANALYSE_RAIN000_IDENTITY_VALUE,
                    RedisKeyConstant.ANALYSE_RAIN000_IDENTITY_VERSION
            ),
            "gopeeddev-identity-analyse", List.of(
                    RedisKeyConstant.ANALYSE_GOPEEDDEV_IDENTITY_VALUE,
                    RedisKeyConstant.ANALYSE_GOPEEDDEV_IDENTITY_VERSION
            ),
            "datacenter-highrisk-analyse", List.of(
                    RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VALUE,
                    RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VERSION
            ),
            "geoip-city-blacklist", List.of(
                    RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VALUE,
                    RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VERSION
            ),
            "btnbypass-userapps", List.of(
                    RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VALUE,
                    RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VERSION
            )
    );

    private final DatabaseConfigService databaseConfigService;
    private final RedisTemplate<String, String> redisTemplate;
    private volatile Map<String, String> cachedValues;
    private volatile long cacheExpiresAt;

    public AnalyseModuleConfigService(
            DatabaseConfigService databaseConfigService,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.databaseConfigService = databaseConfigService;
        this.redisTemplate = redisTemplate;
    }

    public @NotNull List<ModuleView> listModules() {
        Map<String, String> values = rawValues();
        return MODULES.stream().map(module -> new ModuleView(
                module.id(),
                !hasField(module, "enabled")
                        || booleanValue(module, "enabled", true, values),
                module.fields().stream().map(field -> new ModuleFieldView(
                        field.field(),
                        field.key(),
                        field.type(),
                        displayValue(field, values.getOrDefault(field.key(), field.defaultValue())),
                        false
                )).toList()
        )).toList();
    }

    public @NotNull ModuleView update(
            @NotNull String moduleId,
            @NotNull Map<String, String> requestedValues,
            @Nullable Long operatorId
    ) throws BusinessException {
        ModuleDefinition module = MODULE_INDEX.get(moduleId);
        if (module == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Analysis module not found");
        }
        Map<String, ModuleFieldDefinition> fields = module.fields().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ModuleFieldDefinition::field,
                        value -> value
                ));
        Map<String, String> rawValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : requestedValues.entrySet()) {
            ModuleFieldDefinition field = fields.get(entry.getKey());
            if (field == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Unknown module field: " + entry.getKey());
            }
            rawValues.put(field.key(), parseValue(field, entry.getValue()));
        }
        for (ModuleFieldDefinition field : module.fields()) {
            if (!rawValues.containsKey(field.key())) {
                continue;
            }
            String value = rawValues.get(field.key());
            if (!databaseConfigService.exists(field.key())) {
                databaseConfigService.insertIfMissing(field.key(), field.defaultValue(), "yaml");
            }
            databaseConfigService.update(field.key(), value, operatorId);
        }
        invalidateCache();
        if (requestedValues.containsKey("enabled")
                && !Boolean.parseBoolean(requestedValues.get("enabled"))) {
            clearOutput(moduleId);
        }
        return listModules().stream()
                .filter(moduleView -> moduleView.id().equals(moduleId))
                .findFirst()
                .orElseThrow();
    }

    public boolean isEnabled(String moduleId) {
        ModuleDefinition module = requireModule(moduleId);
        Map<String, String> values = rawValues();
        boolean enabled = booleanValue(module, "enabled", true, values);
        if (!enabled || !"points-dynamic".equals(moduleId)) {
            return enabled;
        }
        ModuleDefinition settings = requireModule("points-settings");
        return booleanValue(settings, "dynamic-seed-upload-enabled", true, values)
                || booleanValue(settings, "dynamic-download-enabled", true, values)
                || booleanValue(settings, "dynamic-hot-peer-enabled", true, values);
    }

    public long durationMillis(String moduleId, String field, long fallback) {
        ModuleDefinition module = requireModule(moduleId);
        String value = fieldValue(module, field, String.valueOf(fallback), rawValues());
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public int integerValue(String moduleId, String field, int fallback) {
        ModuleDefinition module = requireModule(moduleId);
        try {
            return Integer.parseInt(fieldValue(module, field, String.valueOf(fallback), rawValues()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public double decimalValue(String moduleId, String field, double fallback) {
        ModuleDefinition module = requireModule(moduleId);
        try {
            return Double.parseDouble(fieldValue(module, field, String.valueOf(fallback), rawValues()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public long longValue(String moduleId, String field, long fallback) {
        ModuleDefinition module = requireModule(moduleId);
        try {
            return Long.parseLong(fieldValue(module, field, String.valueOf(fallback), rawValues()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public boolean booleanValue(String moduleId, String field, boolean fallback) {
        ModuleDefinition module = requireModule(moduleId);
        return Boolean.parseBoolean(fieldValue(module, field, String.valueOf(fallback), rawValues()));
    }

    public String textValue(String moduleId, String field, String fallback) {
        ModuleDefinition module = requireModule(moduleId);
        return fieldValue(module, field, fallback, rawValues());
    }

    public void invalidateCache() {
        cachedValues = null;
        cacheExpiresAt = 0;
    }

    private boolean booleanValue(
            ModuleDefinition module,
            String field,
            boolean fallback,
            Map<String, String> values
    ) {
        return Boolean.parseBoolean(fieldValue(module, field, String.valueOf(fallback), values));
    }

    private String fieldValue(
            ModuleDefinition module,
            String field,
            String fallback,
            Map<String, String> values
    ) {
        ModuleFieldDefinition definition = module.fields().stream()
                .filter(candidate -> candidate.field().equals(field))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown module field: " + field));
        String value = values.get(definition.key());
        if (value == null || (value.isBlank() && fallback.isBlank())) {
            return definition.defaultValue();
        }
        return value;
    }

    private boolean hasField(ModuleDefinition module, String field) {
        return module.fields().stream()
                .anyMatch(candidate -> candidate.field().equals(field));
    }

    private Map<String, String> rawValues() {
        long now = System.currentTimeMillis();
        if (cachedValues != null && now < cacheExpiresAt) {
            return cachedValues;
        }
        synchronized (this) {
            if (cachedValues != null && now < cacheExpiresAt) {
                return cachedValues;
            }
            List<String> keys = MODULES.stream()
                    .flatMap(module -> module.fields().stream())
                    .map(ModuleFieldDefinition::key)
                    .toList();
            try {
                cachedValues = databaseConfigService.getRawValues(keys);
            } catch (Exception exception) {
                log.warn("Unable to load analysis module configuration, using YAML defaults",
                        exception);
                cachedValues = Map.of();
            }
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return cachedValues;
        }
    }

    private String displayValue(ModuleFieldDefinition field, String rawValue) {
        try {
            return switch (field.type()) {
                case "duration-hours" -> decimal(Long.parseLong(rawValue), HOUR_MILLIS, 2);
                case "bytes-gib" -> decimal(Long.parseLong(rawValue), GIB_BYTES, 2);
                case "bytes-gb" -> decimal(Long.parseLong(rawValue), GIB_BYTES, 2);
                default -> rawValue;
            };
        } catch (NumberFormatException exception) {
            return field.defaultValue();
        }
    }

    private String parseValue(ModuleFieldDefinition field, String displayValue)
            throws BusinessException {
        String value = displayValue == null ? "" : displayValue.trim();
        try {
            return switch (field.type()) {
                case "boolean" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException();
                    }
                    yield Boolean.toString(Boolean.parseBoolean(value));
                }
                case "integer" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0) throw new IllegalArgumentException();
                    yield Integer.toString(parsed);
                }
                case "long" -> {
                    long parsed = Long.parseLong(value);
                    if (parsed < 0) throw new IllegalArgumentException();
                    yield Long.toString(parsed);
                }
                case "decimal" -> {
                    BigDecimal parsed = new BigDecimal(value);
                    if (parsed.signum() < 0) throw new IllegalArgumentException();
                    yield parsed.stripTrailingZeros().toPlainString();
                }
                case "duration-hours" -> {
                    BigDecimal parsed = new BigDecimal(value);
                    if (parsed.signum() <= 0) throw new IllegalArgumentException();
                    yield parsed.multiply(BigDecimal.valueOf(HOUR_MILLIS))
                            .setScale(0, RoundingMode.HALF_UP)
                            .toPlainString();
                }
                case "bytes-gib" -> {
                    BigDecimal parsed = new BigDecimal(value);
                    if (parsed.signum() < 0) throw new IllegalArgumentException();
                    yield parsed.multiply(BigDecimal.valueOf(GIB_BYTES))
                            .setScale(0, RoundingMode.HALF_UP)
                            .toPlainString();
                }
                case "bytes-gb" -> {
                    BigDecimal parsed = new BigDecimal(value);
                    if (parsed.signum() < 0) throw new IllegalArgumentException();
                    yield parsed.multiply(BigDecimal.valueOf(GIB_BYTES))
                            .setScale(0, RoundingMode.HALF_UP)
                            .toPlainString();
                }
                case "cron" -> {
                    if (AnalyseModuleCron.isDisabled(value)) {
                        yield AnalyseModuleCron.DISABLED;
                    }
                    CronExpression.parse(value);
                    yield value;
                }
                case "text" -> {
                    if (value.isBlank()) throw new IllegalArgumentException();
                    yield value;
                }
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Invalid value for module field " + field.field());
        }
    }

    private void clearOutput(String moduleId) {
        List<RedisKeyConstant> keys = OUTPUT_KEYS.get(moduleId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys.stream().map(RedisKeyConstant::getKey).toList());
    }

    private ModuleDefinition requireModule(String moduleId) {
        ModuleDefinition module = MODULE_INDEX.get(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("Analysis module not found: " + moduleId);
        }
        return module;
    }

    private String decimal(long rawValue, long divisor, int scale) {
        return BigDecimal.valueOf(rawValue)
                .divide(BigDecimal.valueOf(divisor), scale, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static ModuleDefinition simpleIdentityModule(String id) {
        return module(id, id,
                bool("enabled", "enabled", true),
                cron("schedule", "schedule", "0 0 * * * *"),
                durationHours("duration", "duration", 1_209_600_000L)
        );
    }

    private static ModuleDefinition module(
            String id,
            String path,
            ModuleFieldDefinition... fields
    ) {
        String prefix = PREFIX + path;
        return new ModuleDefinition(id, java.util.Arrays.stream(fields)
                .map(field -> new ModuleFieldDefinition(
                        field.field(),
                        prefix + "." + field.key(),
                        field.type(),
                        field.defaultValue()
                ))
                .toList());
    }

    private static ModuleFieldDefinition bool(String field, String path, boolean defaultValue) {
        return field(field, path, "boolean", Boolean.toString(defaultValue));
    }

    private static ModuleFieldDefinition integer(String field, String path, int defaultValue) {
        return field(field, path, "integer", Integer.toString(defaultValue));
    }

    private static ModuleFieldDefinition longField(
            String field,
            String path,
            long defaultValue
    ) {
        return field(field, path, "long", Long.toString(defaultValue));
    }

    private static ModuleFieldDefinition decimal(String field, String path, String defaultValue) {
        return field(field, path, "decimal", defaultValue);
    }

    private static ModuleFieldDefinition durationHours(String field, String path, long defaultValue) {
        return field(field, path, "duration-hours", Long.toString(defaultValue));
    }

    private static ModuleFieldDefinition bytesGib(String field, String path, long defaultValue) {
        return field(field, path, "bytes-gib", Long.toString(defaultValue));
    }

    private static ModuleFieldDefinition bytesGb(String field, String path, long defaultValue) {
        return field(field, path, "bytes-gb",
                Long.toString(Math.multiplyExact(defaultValue, GIB_BYTES)));
    }

    private static ModuleFieldDefinition cron(String field, String path, String defaultValue) {
        return field(field, path, "cron", defaultValue);
    }

    private static ModuleFieldDefinition text(String field, String path, String defaultValue) {
        return field(field, path, "text", defaultValue);
    }

    private static ModuleFieldDefinition field(
            String field,
            String path,
            String type,
            String defaultValue
    ) {
        return new ModuleFieldDefinition(field, path, type, defaultValue);
    }

    private record ModuleDefinition(String id, List<ModuleFieldDefinition> fields) {
    }

    private record ModuleFieldDefinition(
            String field,
            String key,
            String type,
            String defaultValue
    ) {
    }
}
