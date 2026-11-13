package com.ghostchu.btn.sparkle.service.ipquery;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class IpQuerySettingsService {
    public static final String COMMENTS_ENABLED =
            "sparkle.ip-query.comments.enabled";
    public static final String COMMENTS_VISIBLE =
            "sparkle.ip-query.comments.visible";
    public static final String MAX_LENGTH =
            "sparkle.ip-query.comments.max-length";
    public static final String HOURLY_LIMIT =
            "sparkle.ip-query.comments.hourly-limit";

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final List<String> KEYS = List.of(
            COMMENTS_ENABLED,
            COMMENTS_VISIBLE,
            MAX_LENGTH,
            HOURLY_LIMIT
    );
    private static final Settings DEFAULTS =
            new Settings(true, true, 5_000, 30);

    private final DatabaseConfigService databaseConfigService;
    private volatile Settings cached;
    private volatile long cacheExpiresAt;

    public IpQuerySettingsService(DatabaseConfigService databaseConfigService) {
        this.databaseConfigService = databaseConfigService;
    }

    public @NotNull Settings get() {
        long now = System.currentTimeMillis();
        Settings current = cached;
        if (current != null && now < cacheExpiresAt) {
            return current;
        }
        synchronized (this) {
            current = cached;
            if (current != null && now < cacheExpiresAt) {
                return current;
            }
            current = load();
            cached = current;
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return current;
        }
    }

    @Transactional
    public @NotNull Settings update(
            boolean commentsEnabled,
            boolean commentsVisible,
            int maxLength,
            int hourlyLimit,
            @Nullable Long operatorId
    ) throws BusinessException {
        if (maxLength < 100 || maxLength > 20_000) {
            throw badRequest("Comment maximum length must be between 100 and 20000");
        }
        if (hourlyLimit < 1 || hourlyLimit > 500) {
            throw badRequest("Comment hourly limit must be between 1 and 500");
        }
        Settings updated = new Settings(
                commentsEnabled,
                commentsVisible,
                maxLength,
                hourlyLimit
        );
        databaseConfigService.update(
                COMMENTS_ENABLED,
                Boolean.toString(commentsEnabled),
                operatorId
        );
        databaseConfigService.update(
                COMMENTS_VISIBLE,
                Boolean.toString(commentsVisible),
                operatorId
        );
        databaseConfigService.update(
                MAX_LENGTH,
                Integer.toString(maxLength),
                operatorId
        );
        databaseConfigService.update(
                HOURLY_LIMIT,
                Integer.toString(hourlyLimit),
                operatorId
        );
        cached = updated;
        cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL.toMillis();
        return updated;
    }

    public void invalidate() {
        cached = null;
        cacheExpiresAt = 0;
    }

    private Settings load() {
        try {
            Map<String, String> values =
                    databaseConfigService.getRawValues(KEYS);
            return new Settings(
                    parseBoolean(
                            values.get(COMMENTS_ENABLED),
                            DEFAULTS.commentsEnabled()
                    ),
                    parseBoolean(
                            values.get(COMMENTS_VISIBLE),
                            DEFAULTS.commentsVisible()
                    ),
                    parseInt(values.get(MAX_LENGTH), DEFAULTS.maxLength()),
                    parseInt(values.get(HOURLY_LIMIT), DEFAULTS.hourlyLimit())
            );
        } catch (Exception exception) {
            log.warn("Unable to load IP query settings: {}",
                    exception.getMessage());
            return cached == null ? DEFAULTS : cached;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        return value == null || value.isBlank()
                ? fallback
                : Boolean.parseBoolean(value);
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank()
                    ? fallback
                    : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    public record Settings(
            boolean commentsEnabled,
            boolean commentsVisible,
            int maxLength,
            int hourlyLimit
    ) {
    }
}
