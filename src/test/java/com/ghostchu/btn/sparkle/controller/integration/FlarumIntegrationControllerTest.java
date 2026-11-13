package com.ghostchu.btn.sparkle.controller.integration;

import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.UserDataResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.FlarumIntegrationService;
import com.ghostchu.btn.sparkle.service.FlarumIntegrationSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlarumIntegrationControllerTest {
    @Test
    void rejectsInvalidTokenBeforeQueryingData() {
        FlarumIntegrationService service = mock(FlarumIntegrationService.class);
        FlarumIntegrationSettingsService settings = mock(FlarumIntegrationSettingsService.class);
        when(settings.token()).thenReturn("secret-token");
        FlarumIntegrationController controller = new FlarumIntegrationController(service, settings);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.userByGithub("12345", "wrong-token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsRequestsWhenTokenIsNotConfigured() {
        FlarumIntegrationService service = mock(FlarumIntegrationService.class);
        FlarumIntegrationSettingsService settings = mock(FlarumIntegrationSettingsService.class);
        when(settings.token()).thenReturn("");
        FlarumIntegrationController controller = new FlarumIntegrationController(service, settings);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.userByGithub("12345", "")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void delegatesValidRequestToIntegrationService() throws BusinessException {
        FlarumIntegrationService service = mock(FlarumIntegrationService.class);
        FlarumIntegrationSettingsService settings = mock(FlarumIntegrationSettingsService.class);
        UserDataResponse response = UserDataResponse.unbound("12345");
        when(service.findByGithubUid("12345")).thenReturn(response);
        when(settings.token()).thenReturn("secret-token");

        FlarumIntegrationController controller = new FlarumIntegrationController(service, settings);
        UserDataResponse result = controller.userByGithub("12345", "secret-token");

        assertTrue(result.githubUid().equals("12345"));
        verify(service).findByGithubUid("12345");
    }
}
