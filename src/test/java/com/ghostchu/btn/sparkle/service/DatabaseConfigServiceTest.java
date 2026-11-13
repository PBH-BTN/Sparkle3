package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.entity.AppConfigEntry;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AppConfigEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseConfigServiceTest {
    private static final String ROOT_URL = "sparkle.root-url";
    private static final String FOOTER_CONFIG = "sparkle.footer.config";
    private static final String INTEGRATION_TOKEN = "sparkle.github-sync.token";

    private AppConfigEntryMapper configMapper;
    private RedisTemplate<String, String> redisTemplate;
    private HashOperations<String, String, String> redisHash;
    private DatabaseConfigService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        configMapper = mock(AppConfigEntryMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        redisHash = mock(HashOperations.class);
        doReturn(redisHash).when(redisTemplate).opsForHash();
        service = new DatabaseConfigService(
                configMapper,
                mock(Environment.class),
                redisTemplate
        );
    }

    @Test
    void onlyManagesBusinessConfigurationNamespace() {
        assertTrue(service.isExcludedKey("spring.datasource.password"));
        assertTrue(service.isExcludedKey("spring.security.oauth2.client.registration.github.client-secret"));
        assertTrue(service.isExcludedKey("server.port"));
        assertTrue(service.isExcludedKey("sentry.dsn"));
        assertFalse(service.isExcludedKey(ROOT_URL));
        assertFalse(service.isExcludedKey("sparkle.analyse.untrusted-vote.schedule"));
    }

    @Test
    void readsRedisFirstAndLoadsOnlyMissingValuesFromDatabase() {
        when(redisHash.multiGet(RedisKeyConstant.APP_CONFIG_VALUES.getKey(),
                List.of(ROOT_URL, FOOTER_CONFIG)))
                .thenReturn(Arrays.asList("cached-root", null));
        when(configMapper.selectBatchIds(List.of(FOOTER_CONFIG)))
                .thenReturn(List.of(entry(FOOTER_CONFIG, "database-footer")));

        Map<String, String> result = service.getRawValues(List.of(ROOT_URL, FOOTER_CONFIG));

        assertEquals(Map.of(
                ROOT_URL, "cached-root",
                FOOTER_CONFIG, "database-footer"
        ), result);
        verify(redisHash).putAll(RedisKeyConstant.APP_CONFIG_VALUES.getKey(),
                Map.of(FOOTER_CONFIG, "database-footer"));
        verify(redisTemplate).expire(
                RedisKeyConstant.APP_CONFIG_VALUES.getKey(),
                Duration.ofMinutes(5)
        );
    }

    @Test
    void bypassesRedisForSensitiveConfiguration() {
        when(configMapper.selectBatchIds(List.of(INTEGRATION_TOKEN)))
                .thenReturn(List.of(entry(INTEGRATION_TOKEN, "secret")));

        Map<String, String> result = service.getRawValues(List.of(INTEGRATION_TOKEN));

        assertEquals(Map.of(INTEGRATION_TOKEN, "secret"), result);
        verify(redisHash, never()).multiGet(any(), any());
        verify(redisHash, never()).putAll(any(), any());
    }

    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        when(redisHash.multiGet(RedisKeyConstant.APP_CONFIG_VALUES.getKey(), List.of(ROOT_URL)))
                .thenThrow(new RedisConnectionFailureException("unavailable"));
        when(configMapper.selectBatchIds(List.of(ROOT_URL)))
                .thenReturn(List.of(entry(ROOT_URL, "https://example.test")));

        Map<String, String> result = service.getRawValues(List.of(ROOT_URL));

        assertEquals(Map.of(ROOT_URL, "https://example.test"), result);
    }

    @Test
    void evictsCacheAfterConfigurationUpdate() throws BusinessException {
        stubUpdatableEntry(ROOT_URL);

        service.update(ROOT_URL, "https://updated.test", 1L);

        verify(redisHash).delete(RedisKeyConstant.APP_CONFIG_VALUES.getKey(), ROOT_URL);
    }

    @Test
    void defersCacheEvictionUntilTransactionCommit() throws BusinessException {
        stubUpdatableEntry(ROOT_URL);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.update(ROOT_URL, "https://updated.test", 1L);

            verify(redisHash, never()).delete(
                    RedisKeyConstant.APP_CONFIG_VALUES.getKey(),
                    ROOT_URL
            );
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(redisHash).delete(RedisKeyConstant.APP_CONFIG_VALUES.getKey(), ROOT_URL);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stubUpdatableEntry(String key) {
        AppConfigEntry entry = entry(key, "https://old.test")
                .setConfigGroup("sparkle")
                .setValueType("string")
                .setSensitive(false);
        when(configMapper.selectById(key)).thenReturn(entry);
        when(configMapper.updateById(entry)).thenReturn(1);
    }

    private AppConfigEntry entry(String key, String value) {
        return new AppConfigEntry()
                .setConfigKey(key)
                .setConfigValue(value);
    }

}
