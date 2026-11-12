package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class BtnRuntimeConfigService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;
    private final Map<String, String> defaults;

    private volatile Map<String, String> cachedValues;
    private volatile long cacheExpiresAt;

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
        Map<String, String> current = cachedValues;
        if (current != null && now < cacheExpiresAt) {
            return current;
        }
        synchronized (this) {
            if (cachedValues != null && now < cacheExpiresAt) {
                return cachedValues;
            }
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
            cachedValues = Map.copyOf(loaded);
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return cachedValues;
        }
    }

    public String text(String section, String field) {
        return raw(BtnConfigCatalog.key(section, field));
    }

    public boolean booleanValue(String section, String field) {
        return Boolean.parseBoolean(text(section, field));
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

    public String defaultValue(String key) {
        BtnConfigCatalog.Definition definition = BtnConfigCatalog.definition(key);
        return definition == null ? null : defaults.get(definition.key());
    }

    public void invalidate() {
        synchronized (this) {
            cachedValues = null;
            cacheExpiresAt = 0;
        }
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
