package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;

@lombok.extern.slf4j.Slf4j
@Service
public class CareerRedisCache {
    private final RedisTemplate<String,String> redis;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public CareerRedisCache(@Qualifier("stringStringRedisTemplate") RedisTemplate<String,String> redis) {
        this.redis = redis;
    }

    public <T> T get(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : json.readValue(value,type);
        } catch (Exception failure) {
            log.debug("Career cache read failed ({})",failure.getClass().getSimpleName());
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key,json.writeValueAsString(value),ttl); }
        catch (Exception failure) {
            log.debug("Career cache write failed ({})",failure.getClass().getSimpleName());
        }
    }

    public String snapshotKey(long uid) {
        return RedisKeyConstant.CAREER_SNAPSHOT.getKey() + ":" + uid + ":" + java.time.LocalDate.now(java.time.ZoneOffset.UTC);
    }

    public boolean alreadyCommitted(String fingerprint) {
        return get(RedisKeyConstant.CAREER_REPORTED.getKey() + ":" + fingerprint, Boolean.class) == Boolean.TRUE;
    }

    public void markAfterCommit(String fingerprint) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                put(RedisKeyConstant.CAREER_REPORTED.getKey() + ":" + fingerprint, true, Duration.ofMinutes(5));
            }
        });
    }
}
