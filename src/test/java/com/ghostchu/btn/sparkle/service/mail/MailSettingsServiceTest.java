package com.ghostchu.btn.sparkle.service.mail;

import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailSettingsServiceTest {
    @Test
    void channelSavePreservesOmittedSenderIdentitiesAndExistingPasswords() throws Exception {
        var database = mock(DatabaseConfigService.class);
        when(database.getRawValues(anyList())).thenReturn(Map.of(
                MailSettingsService.SYSTEM_FROM_ADDRESS, "system@example.invalid",
                MailSettingsService.USER_FROM_ADDRESS, "users@example.invalid",
                MailSettingsService.SMTP_PASSWORD, "test-smtp-password",
                MailSettingsService.POP3_PASSWORD, "test-pop3-password"));
        var service = new MailSettingsService(database, new MockEnvironment(), mock(ApplicationEventPublisher.class));
        var current = service.get();
        var updated = service.update(new MailSettingsService.UpdateRequest(
                false, "", 587, "", null, false, current.smtp().security(), true,
                10, 15, 15, "", "Test sender", false, "", 995, "", null, false,
                current.pop3().security(), "", 60, 15, 100, null, null), 12L);
        assertEquals(current.systemSender(), updated.systemSender());
        assertEquals(current.userSender(), updated.userSender());
        assertEquals(current.smtp().password(), updated.smtp().password());
        assertEquals(current.pop3().password(), updated.pop3().password());
        verify(database, never()).update(startsWith("sparkle.version-alert."), anyString(), any());
        verify(database, never()).update(startsWith("sparkle.user-contact-email."), anyString(), any());
    }
}
