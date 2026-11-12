package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DatabaseConfigServiceTest {
    private final DatabaseConfigService service = new DatabaseConfigService(
            mock(JdbcTemplate.class),
            mock(Environment.class)
    );

    @Test
    void onlyManagesBusinessConfigurationNamespace() {
        assertTrue(service.isExcludedKey("spring.datasource.password"));
        assertTrue(service.isExcludedKey("spring.security.oauth2.client.registration.github.client-secret"));
        assertTrue(service.isExcludedKey("server.port"));
        assertTrue(service.isExcludedKey("sentry.dsn"));
        assertFalse(service.isExcludedKey("sparkle.root-url"));
        assertFalse(service.isExcludedKey("sparkle.analyse.untrusted-vote.schedule"));
    }
}
