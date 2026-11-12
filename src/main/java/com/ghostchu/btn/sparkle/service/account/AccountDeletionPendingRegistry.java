package com.ghostchu.btn.sparkle.service.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
@Slf4j
public class AccountDeletionPendingRegistry {
    private static final String KEY_PREFIX = "sparkle:account-deletion:pending:";
    private static final Duration RETENTION_AFTER_COOLING =
            UserAccountDeletionService.COOLING_PERIOD.plusDays(1);

    private final RedisTemplate<String, String> redisTemplate;

    public AccountDeletionPendingRegistry(
            @Qualifier("stringStringRedisTemplate")
            RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isMarked(long userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
        } catch (Exception exception) {
            log.warn("Unable to inspect pending account deletion marker for user {}: {}",
                    userId, exception.getMessage());
            // Fall back to the database check rather than missing account activity.
            return true;
        }
    }

    public void mark(long userId, OffsetDateTime coolingEndsAt) {
        try {
            redisTemplate.opsForValue().set(
                    key(userId),
                    coolingEndsAt.toString(),
                    RETENTION_AFTER_COOLING
            );
        } catch (Exception exception) {
            log.warn("Unable to store pending account deletion marker for user {}: {}",
                    userId, exception.getMessage());
        }
    }

    public void clear(long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (Exception exception) {
            log.warn("Unable to clear pending account deletion marker for user {}: {}",
                    userId, exception.getMessage());
        }
    }

    private String key(long userId) {
        return KEY_PREFIX + userId;
    }
}
