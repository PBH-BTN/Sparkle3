package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.MigrationResult;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class DatabaseConfigMigrationService implements ApplicationRunner {
    private final ConfigurableEnvironment environment;
    private final ResourceLoader resourceLoader;
    private final DatabaseConfigService configService;
    private final RedisTemplate<String, String> redisTemplate;
    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    public DatabaseConfigMigrationService(
            ConfigurableEnvironment environment,
            ResourceLoader resourceLoader,
            DatabaseConfigService configService,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.environment = environment;
        this.resourceLoader = resourceLoader;
        this.configService = configService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @MonitoredScheduledTask(id = "database-config-migration", group = TaskGroup.STARTUP,
            nameKey = "admin.tasks.items.database-config-migration.name",
            descriptionKey = "admin.tasks.items.database-config-migration.description",
            scheduleType = TaskScheduleType.STARTUP)
    public void run(ApplicationArguments args) {
        MigrationResult result = migrateNow();
        log.info("Database config migration scanned {} YAML entries, inserted {} new keys, skipped {}",
                result.scanned(), result.inserted(), result.skipped());
    }

    public synchronized @NotNull MigrationResult migrateNow() {
        LoadedYamlValues loaded = loadYamlValues();
        Map<String, String> values = new LinkedHashMap<>(loaded.bundled());
        values.putAll(loaded.external());
        values.entrySet().removeIf(entry ->
                !DatabaseConfigService.isManagedKey(entry.getKey())
                        || configService.isDeprecatedKey(entry.getKey())
        );
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            boolean external = loaded.external().containsKey(entry.getKey());
            boolean exists = configService.exists(entry.getKey());
            int changed = external
                    ? configService.upsertYamlValue(entry.getKey(), entry.getValue())
                    : configService.insertIfMissing(entry.getKey(), entry.getValue(), "yaml");
            if (changed > 0) {
                if (exists) {
                    updated++;
                } else {
                    inserted++;
                }
            } else {
                skipped++;
            }
        }
        updated += migrateLegacyRedisOverrides();
        return new MigrationResult(
                values.size(),
                inserted,
                updated,
                skipped,
                OffsetDateTime.now()
        );
    }

    private int migrateLegacyRedisOverrides() {
        Map<String, String> legacyKeys = Map.of(
                "protocolMinVersion", "sparkle.ping.config.min-protocol-version",
                "protocolMaxVersion", "sparkle.ping.config.max-protocol-version",
                "queryIpPowCaptcha", "sparkle.ping.query-ip.pow-captcha",
                "clientDiscoveryEnabled", "sparkle.client-discovery.enabled"
        );
        try {
            Map<Object, Object> legacy = redisTemplate.opsForHash()
                    .entries("sparkle:admin:runtime-config");
            if (legacy == null || legacy.isEmpty()) {
                return 0;
            }
            int updated = 0;
            for (Map.Entry<String, String> entry : legacyKeys.entrySet()) {
                Object value = legacy.get(entry.getKey());
                if (value != null) {
                    updated += configService.overrideYamlValue(entry.getValue(), String.valueOf(value));
                }
            }
            return updated;
        } catch (Exception exception) {
            log.warn("Unable to migrate legacy Redis runtime config: {}", exception.getMessage());
            return 0;
        }
    }

    private LoadedYamlValues loadYamlValues() {
        List<String> bundledLocations = new ArrayList<>();
        bundledLocations.add("classpath:/application.yaml");
        bundledLocations.add("classpath:/application.yml");
        List<String> externalLocations = new ArrayList<>();
        externalLocations.add("file:./config/application.yaml");
        externalLocations.add("file:./config/application.yml");
        externalLocations.add("file:./application.yaml");
        externalLocations.add("file:./application.yml");
        for (String profile : environment.getActiveProfiles()) {
            bundledLocations.add("classpath:/application-" + profile + ".yaml");
            bundledLocations.add("classpath:/application-" + profile + ".yml");
            externalLocations.add("file:./config/application-" + profile + ".yaml");
            externalLocations.add("file:./config/application-" + profile + ".yml");
            externalLocations.add("file:./application-" + profile + ".yaml");
            externalLocations.add("file:./application-" + profile + ".yml");
        }

        return new LoadedYamlValues(
                loadYamlLocations(bundledLocations),
                loadYamlLocations(externalLocations)
        );
    }

    private Map<String, String> loadYamlLocations(List<String> locations) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String location : locations) {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                continue;
            }
            try {
                for (PropertySource<?> propertySource : yamlLoader.load(location, resource)) {
                    if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
                        for (String name : enumerable.getPropertyNames()) {
                            Object value = propertySource.getProperty(name);
                            if (value != null) {
                                values.put(name, String.valueOf(value));
                            }
                        }
                    }
                }
            } catch (Exception exception) {
                log.warn("Unable to read YAML config source {}: {}", location, exception.getMessage());
            }
        }
        return values;
    }

    private record LoadedYamlValues(
            Map<String, String> bundled,
            Map<String, String> external
    ) {
    }
}
