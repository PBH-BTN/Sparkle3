package com.ghostchu.btn.sparkle.service.mail;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.MailSecurity;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.SenderIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailSettingsServiceTest {
    @Test
    void defaultsToDisabledWhenNoDatabaseValuesExist() {
        DatabaseConfigService databaseConfigService = mock(DatabaseConfigService.class);
        when(databaseConfigService.getRawValues(anyList())).thenReturn(Map.of());
        MailSettingsService service = new MailSettingsService(
                databaseConfigService,
                mock(Environment.class),
                mock(ApplicationEventPublisher.class)
        );

        MailRuntimeSettings settings = service.get();

        assertFalse(settings.smtp().enabled());
        assertFalse(settings.pop3().enabled());
        assertFalse(service.replyByEmailAvailable(settings));
    }

    @Test
    void savesCompleteSettingsAndPreservesPasswordWhenBlank() throws Exception {
        DatabaseConfigService databaseConfigService = mock(DatabaseConfigService.class);
        when(databaseConfigService.getRawValues(anyList())).thenReturn(Map.of(
                MailSettingsService.SMTP_PASSWORD, "existing-secret",
                MailSettingsService.SMTP_ENABLED, "true",
                MailSettingsService.SMTP_HOST, "smtp.example.com",
                MailSettingsService.SMTP_USERNAME, "smtp-user",
                MailSettingsService.SMTP_FROM_ADDRESS, "sparkle@example.com",
                MailSettingsService.SYSTEM_FROM_ADDRESS, "system@example.com",
                MailSettingsService.USER_FROM_ADDRESS, "user@example.com"
        ));
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        MailSettingsService service = new MailSettingsService(
                databaseConfigService,
                mock(Environment.class),
                publisher
        );

        MailRuntimeSettings updated = service.update(new MailSettingsService.UpdateRequest(
                true,
                "smtp.example.com",
                465,
                "smtp-user",
                null,
                false,
                MailSecurity.SSL,
                true,
                10,
                15,
                15,
                "",
                "Sparkle BTN",
                true,
                "pop.example.com",
                995,
                "inbox@example.com",
                "pop-secret",
                false,
                MailSecurity.SSL,
                "inbox@example.com",
                60,
                15,
                100,
                new SenderIdentity("system@example.com", "System"),
                new SenderIdentity("user@example.com", "User mail")
        ), 42L);

        assertTrue(service.smtpConfigured(updated));
        assertTrue(service.pop3Configured(updated));
        assertTrue(service.replyByEmailAvailable(updated));
        verify(databaseConfigService).updateSensitive(
                MailSettingsService.SMTP_PASSWORD,
                "existing-secret",
                42L
        );
        verify(databaseConfigService).updateSensitive(
                MailSettingsService.POP3_PASSWORD,
                "pop-secret",
                42L
        );
        verify(publisher).publishEvent(new MailSettingsChangedEvent());
    }

    @Test
    void rejectsEnabledSmtpWithoutHost() {
        DatabaseConfigService databaseConfigService = mock(DatabaseConfigService.class);
        when(databaseConfigService.getRawValues(anyList())).thenReturn(Map.of());
        MailSettingsService service = new MailSettingsService(
                databaseConfigService,
                mock(Environment.class),
                mock(ApplicationEventPublisher.class)
        );

        assertThrows(BusinessException.class, () -> service.update(
                new MailSettingsService.UpdateRequest(
                        true, "", 587, "user", "secret", false,
                        MailSecurity.STARTTLS, true, 10, 15, 15,
                        "", "Sparkle BTN",
                        false, "", 995, "", "", false, MailSecurity.SSL, "",
                        60, 15, 100,
                        new SenderIdentity("system@example.com", "System"),
                        new SenderIdentity("user@example.com", "User")
                ),
                1L
        ));
    }
}
