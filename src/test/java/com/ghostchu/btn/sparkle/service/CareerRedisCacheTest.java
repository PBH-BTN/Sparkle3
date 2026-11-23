package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CareerRedisCacheTest {
    @Test
    void duplicateMarkersDoNotPerformRedisIoInsideAnOuterTransaction() {
        RedisTemplate<String,String> redis = mock(RedisTemplate.class);
        ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var cache = new CareerRedisCache(redis);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertFalse(cache.alreadyCommitted("test"));
            cache.markCommitted("test");
            verifyNoInteractions(redis);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        cache.markCommitted("test");
        verify(values).set(anyString(), eq("true"), eq(java.time.Duration.ofMinutes(5)));
    }

    @Test
    void redisFailureAndCorruptDataAreCacheMisses() {
        RedisTemplate<String,String> redis = mock(RedisTemplate.class);
        ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("bad")).thenReturn("invalid json");
        var cache = new CareerRedisCache(redis);
        assertNull(cache.get("bad",Boolean.class));
        when(values.get("bad")).thenThrow(new IllegalStateException("offline"));
        assertNull(cache.get("bad",Boolean.class));
        assertFalse(cache.alreadyCommitted("other"));
    }
}
