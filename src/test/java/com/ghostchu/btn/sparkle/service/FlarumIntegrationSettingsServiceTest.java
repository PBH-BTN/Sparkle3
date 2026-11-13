package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlarumIntegrationSettingsServiceTest {
    private final DatabaseConfigService databaseConfigService = mock(DatabaseConfigService.class);
    private final Environment environment = mock(Environment.class);
    private final FlarumIntegrationSettingsService service =
            new FlarumIntegrationSettingsService(databaseConfigService, environment);

    @Test
    void databaseTokenTakesPriorityOverEnvironment() {
        when(databaseConfigService.getRawValues(List.of(FlarumIntegrationSettingsService.TOKEN_KEY)))
                .thenReturn(Map.of(FlarumIntegrationSettingsService.TOKEN_KEY, "database-secret"));

        assertEquals("database-secret", service.token());
        verify(environment, never()).getProperty(
                FlarumIntegrationSettingsService.TOKEN_KEY,
                ""
        );
    }

    @Test
    void fallsBackToEnvironmentWhenDatabaseEntryIsMissing() {
        when(databaseConfigService.getRawValues(List.of(FlarumIntegrationSettingsService.TOKEN_KEY)))
                .thenReturn(Map.of());
        when(environment.getProperty(
                FlarumIntegrationSettingsService.TOKEN_KEY,
                ""
        )).thenReturn("environment-secret");

        assertEquals("environment-secret", service.token());
    }

    @Test
    void rejectsShortTokens() {
        when(databaseConfigService.exists(FlarumIntegrationSettingsService.TOKEN_KEY))
                .thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.update("short", false, 1L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void writesSensitiveTokenToDatabase() throws BusinessException {
        when(databaseConfigService.exists(FlarumIntegrationSettingsService.TOKEN_KEY))
                .thenReturn(true);
        when(databaseConfigService.getRawValues(List.of(FlarumIntegrationSettingsService.TOKEN_KEY)))
                .thenReturn(Map.of(FlarumIntegrationSettingsService.TOKEN_KEY, "0123456789abcdef"));
        when(databaseConfigService.list(null, "sparkle.flarum-integration"))
                .thenReturn(List.of());

        service.update("0123456789abcdef", false, 7L);

        verify(databaseConfigService).updateSensitive(
                FlarumIntegrationSettingsService.TOKEN_KEY,
                "0123456789abcdef",
                7L
        );
    }
}
