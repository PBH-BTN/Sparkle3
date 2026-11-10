package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.AdminResponse;
import com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.ConfigUpdate;
import com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.ModuleConfig;
import com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.ModuleStatus;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.impl.StatisticsRefreshServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class HomepageStatisticsRefreshService {
    private static final Duration CONFIG_CACHE_TTL = Duration.ofSeconds(5);
    private static final String CONFIG_PREFIX = "sparkle.homepage-statistics-refresh.modules.";

    private final StatisticsRefreshServiceImpl statisticsRefreshService;
    private final HomeStatisticsService homeStatisticsService;
    private final DatabaseConfigService databaseConfigService;
    private final ConcurrentMap<String, AtomicBoolean> running = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ModuleRunState> runStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> nextRefreshAt = new ConcurrentHashMap<>();
    private volatile List<ModuleConfig> cachedConfig;
    private volatile long configExpiresAt;

    public HomepageStatisticsRefreshService(
            StatisticsRefreshServiceImpl statisticsRefreshService,
            HomeStatisticsService homeStatisticsService,
            DatabaseConfigService databaseConfigService
    ) {
        this.statisticsRefreshService = statisticsRefreshService;
        this.homeStatisticsService = homeStatisticsService;
        this.databaseConfigService = databaseConfigService;
        long dueAt = System.currentTimeMillis();
        for (Module module : Module.values()) {
            running.put(module.id(), new AtomicBoolean());
            nextRefreshAt.put(module.id(), dueAt + Duration.ofMinutes(module.defaultIntervalMinutes()).toMillis());
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void runScheduledRefreshes() {
        long now = System.currentTimeMillis();
        for (ModuleConfig config : getConfig()) {
            if (!config.enabled()) {
                continue;
            }
            long dueAt = nextRefreshAt.computeIfAbsent(
                    config.module(),
                    ignored -> now + Duration.ofMinutes(config.refreshIntervalMinutes()).toMillis()
            );
            if (now >= dueAt) {
                Module module = Module.BY_ID.get(config.module());
                if (module != null) {
                    startRefresh(module, config, "SCHEDULED", null);
                }
            }
        }
    }

    public @NotNull AdminResponse getStatus() {
        List<ModuleStatus> modules = getConfig().stream()
                .map(this::toStatus)
                .toList();
        return new AdminResponse(modules, OffsetDateTime.now());
    }

    @Transactional
    public @NotNull AdminResponse updateConfig(
            @NotNull ConfigUpdate update,
            @Nullable Long operatorId
    ) throws BusinessException {
        if (update == null || update.modules() == null || update.modules().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "At least one module is required");
        }

        Map<String, ModuleConfig> merged = new LinkedHashMap<>();
        for (ModuleConfig config : getConfig()) {
            merged.put(config.module(), config);
        }
        for (ModuleConfig config : update.modules()) {
            if (config == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Homepage statistics module config is required");
            }
            Module module = Module.byId(config.module());
            if (config.refreshIntervalMinutes() < 1
                    || config.refreshIntervalMinutes() > 10_080) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Refresh interval must be between 1 and 10080 minutes");
            }
            merged.put(module.id(), new ModuleConfig(
                    module.id(),
                    config.enabled(),
                    config.refreshIntervalMinutes()
            ));
        }

        for (ModuleConfig config : update.modules()) {
            updateConfigValue(config, operatorId);
        }

        List<ModuleConfig> saved = List.copyOf(merged.values());
        cachedConfig = saved;
        configExpiresAt = System.currentTimeMillis() + CONFIG_CACHE_TTL.toMillis();
        for (ModuleConfig config : saved) {
            nextRefreshAt.put(
                    config.module(),
                    System.currentTimeMillis()
                            + Duration.ofMinutes(config.refreshIntervalMinutes()).toMillis()
            );
        }
        return getStatus();
    }

    public @NotNull AdminResponse requestRefresh(
            @Nullable String requestedModule,
            @Nullable Long operatorId
    ) throws BusinessException {
        if (requestedModule == null
                || requestedModule.isBlank()
                || "all".equalsIgnoreCase(requestedModule.trim())) {
            for (ModuleConfig config : getConfig()) {
                startRefresh(Module.byId(config.module()), config, "MANUAL", operatorId);
            }
            return getStatus();
        }

        Module module = Module.byId(requestedModule.trim());
        ModuleConfig config = getConfig().stream()
                .filter(item -> item.module().equals(module.id()))
                .findFirst()
                .orElseThrow();
        startRefresh(module, config, "MANUAL", operatorId);
        return getStatus();
    }

    private void updateConfigValue(ModuleConfig config, @Nullable Long operatorId)
            throws BusinessException {
        databaseConfigService.update(
                CONFIG_PREFIX + config.module() + ".enabled",
                String.valueOf(config.enabled()),
                operatorId
        );
        databaseConfigService.update(
                CONFIG_PREFIX + config.module() + ".refresh-interval-minutes",
                String.valueOf(config.refreshIntervalMinutes()),
                operatorId
        );
    }

    private boolean startRefresh(
            Module module,
            ModuleConfig config,
            String trigger,
            @Nullable Long operatorId
    ) {
        AtomicBoolean moduleRunning = running.get(module.id());
        if (!moduleRunning.compareAndSet(false, true)) {
            return false;
        }

        OffsetDateTime startedAt = OffsetDateTime.now();
        nextRefreshAt.put(
                module.id(),
                startedAt.plusMinutes(config.refreshIntervalMinutes()).toInstant().toEpochMilli()
        );
        runStates.put(module.id(), new ModuleRunState(
                startedAt,
                null,
                0,
                trigger,
                operatorId,
                null
        ));

        log.info("Starting homepage statistics refresh for {} ({}, operator {})",
                module.id(), trigger, operatorId);
        Thread.startVirtualThread(() -> {
            long startedNanos = System.nanoTime();
            try {
                execute(module);
                long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
                runStates.put(module.id(), new ModuleRunState(
                        startedAt,
                        OffsetDateTime.now(),
                        durationMs,
                        trigger,
                        operatorId,
                        null
                ));
                log.info("Refreshed homepage statistics module {} in {}ms",
                        module.id(), durationMs);
            } catch (Exception exception) {
                long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
                String message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                runStates.put(module.id(), new ModuleRunState(
                        startedAt,
                        OffsetDateTime.now(),
                        durationMs,
                        trigger,
                        operatorId,
                        message
                ));
                log.error("Unable to refresh homepage statistics module {}", module.id(), exception);
            } finally {
                moduleRunning.set(false);
            }
        });
        return true;
    }

    private void execute(Module module) throws Exception {
        switch (module) {
            case BAN_HISTORY -> statisticsRefreshService.onBanCountRefresh();
            case ACTIVE_APPS -> statisticsRefreshService.onUserAppUpdate();
            case SWARM_TRACKER -> statisticsRefreshService.onSwarmTracker();
            case TORRENTS -> statisticsRefreshService.onTorrentUpdate();
            case NETWORK_CHARTS -> homeStatisticsService.refreshNetworkStatistics();
            case COMPANION_CHARTS -> homeStatisticsService.refreshCompanionStatistics();
        }
    }

    private @NotNull List<ModuleConfig> getConfig() {
        long now = System.currentTimeMillis();
        if (cachedConfig != null && now < configExpiresAt) {
            return cachedConfig;
        }
        synchronized (this) {
            if (cachedConfig != null && now < configExpiresAt) {
                return cachedConfig;
            }
            cachedConfig = loadConfig();
            configExpiresAt = now + CONFIG_CACHE_TTL.toMillis();
            return cachedConfig;
        }
    }

    private @NotNull List<ModuleConfig> loadConfig() {
        List<String> keys = new ArrayList<>();
        for (Module module : Module.values()) {
            keys.add(configKey(module, "enabled"));
            keys.add(configKey(module, "refresh-interval-minutes"));
        }
        try {
            Map<String, String> values = databaseConfigService.getRawValues(keys);
            List<ModuleConfig> configs = new ArrayList<>();
            for (Module module : Module.values()) {
                boolean enabled = parseBoolean(
                        values.get(configKey(module, "enabled")),
                        true
                );
                int interval = parseInterval(
                        values.get(configKey(module, "refresh-interval-minutes")),
                        module.defaultIntervalMinutes()
                );
                configs.add(new ModuleConfig(module.id(), enabled, interval));
            }
            return List.copyOf(configs);
        } catch (Exception exception) {
            log.warn("Unable to load homepage statistics refresh config: {}",
                    exception.getMessage());
            return cachedConfig == null
                    ? defaults()
                    : cachedConfig;
        }
    }

    private List<ModuleConfig> defaults() {
        return java.util.Arrays.stream(Module.values())
                .map(module -> new ModuleConfig(
                        module.id(),
                        true,
                        module.defaultIntervalMinutes()
                ))
                .toList();
    }

    private ModuleStatus toStatus(ModuleConfig config) {
        ModuleRunState state = runStates.get(config.module());
        AtomicBoolean moduleRunning = running.get(config.module());
        Long nextAt = config.enabled() ? nextRefreshAt.get(config.module()) : null;
        return new ModuleStatus(
                config.module(),
                config.enabled(),
                config.refreshIntervalMinutes(),
                moduleRunning != null && moduleRunning.get(),
                state == null ? null : state.startedAt(),
                state == null ? null : state.finishedAt(),
                nextAt == null ? null : OffsetDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(nextAt),
                        ZoneOffset.UTC
                ),
                state == null ? 0 : state.durationMs(),
                state == null ? null : state.trigger(),
                state == null ? null : state.triggeredBy(),
                state == null ? null : state.error()
        );
    }

    private String configKey(Module module, String suffix) {
        return CONFIG_PREFIX + module.id() + "." + suffix;
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }

    private int parseInterval(String value, int fallback) {
        try {
            int parsed = value == null ? fallback : Integer.parseInt(value);
            return parsed >= 1 && parsed <= 10_080 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record ModuleRunState(
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            long durationMs,
            String trigger,
            Long triggeredBy,
            String error
    ) {
    }

    private enum Module {
        BAN_HISTORY("ban-history", 10),
        ACTIVE_APPS("active-apps", 30),
        SWARM_TRACKER("swarm-tracker", 20),
        TORRENTS("torrents", 40),
        NETWORK_CHARTS("network-charts", 5),
        COMPANION_CHARTS("companion-charts", 5);

        private static final Map<String, Module> BY_ID = java.util.Arrays.stream(values())
                .collect(java.util.stream.Collectors.toMap(
                        Module::id,
                        module -> module,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        private final String id;
        private final int defaultIntervalMinutes;

        Module(String id, int defaultIntervalMinutes) {
            this.id = id;
            this.defaultIntervalMinutes = defaultIntervalMinutes;
        }

        public String id() {
            return id;
        }

        public int defaultIntervalMinutes() {
            return defaultIntervalMinutes;
        }

        public static Module byId(String id) throws BusinessException {
            Module module = BY_ID.get(id);
            if (module == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Unknown homepage statistics module: " + id);
            }
            return module;
        }
    }
}
