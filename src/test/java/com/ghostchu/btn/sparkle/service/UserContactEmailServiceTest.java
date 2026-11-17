package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ContactEmailRequest;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserContactEmailDeliveryMapper;
import com.ghostchu.btn.sparkle.service.mail.MailDeliveryException;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings;
import com.ghostchu.btn.sparkle.service.mail.MailSettingsService;
import com.ghostchu.btn.sparkle.service.mail.SmtpMailService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserContactEmailServiceTest {
    @Test
    void smtpFailureReleasesPreviouslyCommittedReservation() throws Exception {
        var deliveries = mock(UserContactEmailDeliveryMapper.class);
        var profiles = mock(UserPublicProfileService.class);
        var config = mock(AdminConfigService.class);
        var smtp = mock(SmtpMailService.class);
        var mail = mock(MailSettingsService.class);
        var reservations = mock(UserContactEmailReservationService.class);
        var sender = new User().setId(1L).setEmail("sender@example.invalid").setNickname("Sender");
        var recipient = new User().setId(2L).setEmail("recipient@example.invalid").setNickname("Recipient");
        when(profiles.requireUser(1)).thenReturn(sender);
        when(profiles.requireUser(2)).thenReturn(recipient);
        when(profiles.canContactEmail(sender, recipient)).thenReturn(true);
        var settings = mock(RuntimeConfigView.class);
        when(config.get()).thenReturn(settings);
        when(settings.userContactEmailSubjectTemplate()).thenReturn("{{title}}");
        when(settings.userContactEmailBodyTemplate()).thenReturn("{{content}}");
        when(mail.get()).thenReturn(mock(MailRuntimeSettings.class));
        when(reservations.reserve(any(), eq(15))).thenAnswer(call -> {
            call.getArgument(0, com.ghostchu.btn.sparkle.entity.UserContactEmailDelivery.class).setId(42L);
            return 14;
        });
        when(smtp.send(any(), anyString(), anyString(), anyString(), isNull()))
                .thenThrow(new MailDeliveryException("unavailable"));
        var service = new UserContactEmailService(deliveries, profiles, config, smtp, mail, reservations);

        assertThrows(BusinessException.class,
                () -> service.send(1, 2, new ContactEmailRequest("Subject", "Body"), null, null));

        var order = inOrder(reservations, smtp, deliveries);
        order.verify(reservations).reserve(any(), eq(15));
        order.verify(smtp).send(any(), eq("recipient@example.invalid"), eq("Subject"), eq("Body"), isNull());
        order.verify(deliveries).deleteById(42L);
    }
}
