package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminConfigService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final String MIN_PROTOCOL = "sparkle.ping.config.min-protocol-version";
    private static final String MAX_PROTOCOL = "sparkle.ping.config.max-protocol-version";
    private static final String QUERY_IP_POW = "sparkle.ping.query-ip.pow-captcha";
    private static final String CLIENT_DISCOVERY = "sparkle.client-discovery.enabled";
    private static final String SWARM_STATISTICS_ENABLED = "sparkle.swarm-statistics-track.enabled";
    private static final String SWARM_STATISTICS_REFRESH_MINUTES =
            "sparkle.swarm-statistics-track.refresh-interval-minutes";
    private static final String SWARM_STATISTICS_WINDOW_DAYS =
            "sparkle.swarm-statistics-track.window-days";

    private final DatabaseConfigService databaseConfigService;
    private final RuntimeConfigView defaults;
    private volatile RuntimeConfigView cached;
    private volatile long cacheExpiresAt;

    public AdminConfigService(DatabaseConfigService databaseConfigService,
                              @Value("${sparkle.ping.config.min-protocol-version}") int protocolMinVersion,
                              @Value("${sparkle.ping.config.max-protocol-version}") int protocolMaxVersion,
                              @Value("${sparkle.ping.query-ip.pow-captcha}") boolean queryIpPowCaptcha,
                              @Value("${sparkle.client-discovery.enabled}") boolean clientDiscoveryEnabled,
                              @Value("${sparkle.swarm-statistics-track.enabled:true}")
                              boolean swarmStatisticsEnabled,
                              @Value("${sparkle.swarm-statistics-track.refresh-interval-minutes:60}")
                              int swarmStatisticsRefreshMinutes,
                              @Value("${sparkle.swarm-statistics-track.window-days:7}")
                              int swarmStatisticsWindowDays) {
        this.databaseConfigService = databaseConfigService;
        this.defaults = new RuntimeConfigView(
                protocolMinVersion,
                protocolMaxVersion,
                queryIpPowCaptcha,
                clientDiscoveryEnabled,
                swarmStatisticsEnabled,
                swarmStatisticsRefreshMinutes,
                swarmStatisticsWindowDays
        );
    }

    public @NotNull RuntimeConfigView get() {
        long now = System.currentTimeMillis();
        if (cached != null && now < cacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (cached != null && now < cacheExpiresAt) {
                return cached;
            }
            cached = load();
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return cached;
        }
    }

    @Transactional
    public @NotNull RuntimeConfigView update(@NotNull RuntimeConfigView config, Long operatorId)
            throws BusinessException {
        validate(config);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MIN_PROTOCOL, String.valueOf(config.protocolMinVersion()));
        values.put(MAX_PROTOCOL, String.valueOf(config.protocolMaxVersion()));
        values.put(QUERY_IP_POW, String.valueOf(config.queryIpPowCaptcha()));
        values.put(CLIENT_DISCOVERY, String.valueOf(config.clientDiscoveryEnabled()));
        values.put(SWARM_STATISTICS_ENABLED, String.valueOf(config.swarmStatisticsEnabled()));
        values.put(SWARM_STATISTICS_REFRESH_MINUTES,
                String.valueOf(config.swarmStatisticsRefreshMinutes()));
        values.put(SWARM_STATISTICS_WINDOW_DAYS, String.valueOf(config.swarmStatisticsWindowDays()));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            databaseConfigService.update(entry.getKey(), entry.getValue(), operatorId);
        }
        cached = normalized(config);
        cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL.toMillis();
        return cached;
    }

    private RuntimeConfigView load() {
        try {
            Map<String, String> entries = databaseConfigService.getRawValues(List.of(
                    MIN_PROTOCOL,
                    MAX_PROTOCOL,
                    QUERY_IP_POW,
                    CLIENT_DISCOVERY,
                    SWARM_STATISTICS_ENABLED,
                    SWARM_STATISTICS_REFRESH_MINUTES,
                    SWARM_STATISTICS_WINDOW_DAYS
            ));
            if (entries == null || entries.isEmpty()) {
                return defaults;
            }
            return new RuntimeConfigView(
                    parseInt(entries.get(MIN_PROTOCOL), defaults.protocolMinVersion()),
                    parseInt(entries.get(MAX_PROTOCOL), defaults.protocolMaxVersion()),
                    parseBoolean(entries.get(QUERY_IP_POW), defaults.queryIpPowCaptcha()),
                    parseBoolean(entries.get(CLIENT_DISCOVERY), defaults.clientDiscoveryEnabled()),
                    parseBoolean(entries.get(SWARM_STATISTICS_ENABLED), defaults.swarmStatisticsEnabled()),
                    parseInt(entries.get(SWARM_STATISTICS_REFRESH_MINUTES),
                            defaults.swarmStatisticsRefreshMinutes()),
                    parseInt(entries.get(SWARM_STATISTICS_WINDOW_DAYS),
                            defaults.swarmStatisticsWindowDays())
            );
        } catch (Exception exception) {
            return cached == null ? defaults : cached;
        }
    }

    private void validate(RuntimeConfigView config) throws BusinessException {
        if (config.protocolMinVersion() < 1
                || config.protocolMaxVersion() < config.protocolMinVersion()
                || config.protocolMaxVersion() > 10_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid BTN protocol version range");
        }
        if (config.swarmStatisticsRefreshMinutes() < 1
                || config.swarmStatisticsRefreshMinutes() > 10_080) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Swarm statistics refresh interval must be between 1 and 10080 minutes");
        }
        if (config.swarmStatisticsWindowDays() < 1
                || config.swarmStatisticsWindowDays() > 90) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Swarm statistics window must be between 1 and 90 days");
        }
    }

    private RuntimeConfigView normalized(RuntimeConfigView config) {
        return new RuntimeConfigView(
                config.protocolMinVersion(),
                config.protocolMaxVersion(),
                config.queryIpPowCaptcha(),
                config.clientDiscoveryEnabled(),
                config.swarmStatisticsEnabled(),
                config.swarmStatisticsRefreshMinutes(),
                config.swarmStatisticsWindowDays()
        );
    }

    private int parseInt(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

}
