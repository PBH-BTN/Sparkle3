package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GithubSyncSettingsServiceTest {
    @Test
    void rejectsInvalidCronBeforeWritingSettings() {
        DatabaseConfigService databaseConfigService = mock(DatabaseConfigService.class);
        Environment environment = mock(Environment.class);
        GithubSyncSettingsService service = new GithubSyncSettingsService(
                databaseConfigService,
                environment
        );

        assertThrows(
                BusinessException.class,
                () -> service.update("* * *", null, false, null)
        );
        verifyNoInteractions(databaseConfigService);
    }
}
