package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class BtnRuntimeConfigService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;
    private final Map<String, String> defaults;

    private record Cache(Map<String, String> values, long expiresAt) {}

    private final AtomicReference<Cache> cache = new AtomicReference<>(new Cache(Map.of(), 0));

    public BtnRuntimeConfigService(
            DatabaseConfigService databaseConfigService,
            Environment environment
    ) {
        this.databaseConfigService = databaseConfigService;
        this.environment = environment;
        Map<String, String> defaults = new LinkedHashMap<>();
        for (BtnConfigCatalog.Definition definition : BtnConfigCatalog.definitions()) {
            defaults.put(
                    definition.key(),
                    environment.getProperty(definition.key(), definition.defaultValue())
            );
        }
        this.defaults = Map.copyOf(defaults);
    }

    public @NotNull Map<String, String> values() {
        long now = System.currentTimeMillis();
        Cache current = cache.get();
        if (now < current.expiresAt()) {
            return current.values();
        }
        // The cache loader may be waiting for a JDBC connection. Reuse the caller's transaction.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return loadValues();
        }
        synchronized (this) {
            current = cache.get();
            if (now < current.expiresAt()) {
                return current.values();
            }
            Map<String, String> loaded = loadValues();
            cache.compareAndSet(current, new Cache(loaded, now + CACHE_TTL.toMillis()));
            return loaded;
        }
    }

    private Map<String, String> loadValues() {
        Map<String, String> loaded = new LinkedHashMap<>(defaults);
        try {
            Map<String, String> databaseValues = databaseConfigService
                    .getRawValues(BtnConfigCatalog.keys());
            loaded.putAll(databaseValues);
            for (String key : BtnConfigCatalog.keys()) {
                String databaseValue = loaded.get(key);
                if (databaseConfigService.hasExternalOverride(key, databaseValue)) {
                    loaded.put(key, environment.getProperty(key, databaseValue));
                }
            }
        } catch (Exception exception) {
            log.warn("Unable to load BTN runtime configuration: {}", exception.getMessage());
        }
        return Map.copyOf(loaded);
    }

    public String text(String section, String field) {
        return raw(BtnConfigCatalog.key(section, field));
    }

    public boolean booleanValue(String section, String field) {
        return Boolean.parseBoolean(text(section, field));
    }

    /** Maximum configured reporting gap, including the client's randomized delay. */
    public long reportingWindowMillis(String section) {
        return Math.addExact(longValue(section,"interval"),longValue(section,"randomInitialDelay"));
    }

    public long longValue(String section, String field) {
        BtnConfigCatalog.Definition definition = definition(section, field);
        String value = text(section, field);
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            parsed = Long.parseLong(definition.defaultValue());
        }
        if (definition.min() != null) {
            parsed = Math.max(definition.min(), parsed);
        }
        if (definition.max() != null) {
            parsed = Math.min(definition.max(), parsed);
        }
        return parsed;
    }

    public int intValue(String section, String field) {
        return Math.toIntExact(longValue(section, field));
    }

    public double doubleValue(String section, String field) {
        BtnConfigCatalog.Definition definition = definition(section, field);
        double parsed;
        try {
            parsed = Double.parseDouble(text(section, field));
        } catch (NumberFormatException exception) {
            parsed = Double.parseDouble(definition.defaultValue());
        }
        if (definition.min() != null) {
            parsed = Math.max(definition.min(), parsed);
        }
        if (definition.max() != null) {
            parsed = Math.min(definition.max(), parsed);
        }
        return parsed;
    }

    public String routeUrl(String isp, String province) {
        Map<String, String> values = values();
        boolean optimized = Boolean.parseBoolean(values.get(BtnConfigCatalog.key("rootUrls", "autoRouteEnabled")))
                && (matchesRoute(values.get(BtnConfigCatalog.key("rootUrls", "chnRouteIsps")), isp)
                || matchesRoute(values.get(BtnConfigCatalog.key("rootUrls", "chnRouteProvinces")), province));
        return values.get(BtnConfigCatalog.key("rootUrls", optimized ? "chnRootUrl" : "rootUrl"));
    }

    private boolean matchesRoute(String keywords, String value) {
        return value != null && keywords != null && Arrays.stream(keywords.split("[,\r\n]"))
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .anyMatch(value::contains);
    }

    public String defaultValue(String key) {
        BtnConfigCatalog.Definition definition = BtnConfigCatalog.definition(key);
        return definition == null ? null : defaults.get(definition.key());
    }

    public void invalidate() {
        cache.set(new Cache(Map.of(), 0));
    }

    private String raw(String key) {
        String value = values().get(key);
        if (value != null) {
            return value;
        }
        BtnConfigCatalog.Definition definition = BtnConfigCatalog.definition(key);
        return definition == null ? "" : definition.defaultValue();
    }

    private BtnConfigCatalog.Definition definition(String section, String field) {
        BtnConfigCatalog.Definition definition = BtnConfigCatalog.definition(
                BtnConfigCatalog.key(section, field)
        );
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Unknown BTN config field: " + section + "." + field
            );
        }
        return definition;
    }
}
