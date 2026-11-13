package com.ghostchu.btn.sparkle.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BittorrentTrafficFeatureService {
    public static final String ENABLED_CONFIG_KEY =
            "sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.enabled";

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final DatabaseConfigService databaseConfigService;
    private volatile Boolean enabled;
    private volatile long cacheExpiresAt;

    public BittorrentTrafficFeatureService(DatabaseConfigService databaseConfigService) {
        this.databaseConfigService = databaseConfigService;
    }

    public boolean isEnabled() {
        long now = System.currentTimeMillis();
        Boolean cached = enabled;
        if (cached != null && now < cacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (enabled != null && now < cacheExpiresAt) {
                return enabled;
            }
            try {
                Map<String, String> values = databaseConfigService.getRawValues(
                        List.of(ENABLED_CONFIG_KEY)
                );
                enabled = parseBoolean(values.get(ENABLED_CONFIG_KEY));
            } catch (Exception exception) {
                log.warn("Unable to load BitTorrent traffic feature state: {}",
                        exception.getMessage());
                if (enabled == null) {
                    enabled = true;
                }
            }
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return enabled;
        }
    }

    public void invalidate() {
        enabled = null;
        cacheExpiresAt = 0;
    }

    private boolean parseBoolean(String value) {
        return value == null || Boolean.parseBoolean(value);
    }
}
