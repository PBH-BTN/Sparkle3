package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.BanReplaySuspensionMapper;
import com.ghostchu.btn.sparkle.mapper.BanReplaySuspensionMapper.MailStatus;
import com.ghostchu.btn.sparkle.mapper.BanReplaySuspensionMapper.Notice;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.mail.MailDeliveryException;
import com.ghostchu.btn.sparkle.service.mail.SmtpMailService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.*;

class BanReplaySuspensionServiceTest {
    private final BanReplaySuspensionMapper mapper = mock(BanReplaySuspensionMapper.class);
    private final BtnRuntimeConfigService config = mock(BtnRuntimeConfigService.class);
    private final SmtpMailService smtp = mock(SmtpMailService.class);
    private final BanReplaySuspensionService service = new BanReplaySuspensionService(mapper, config, smtp);

    @Test
    void checksDisabledAndUnconfiguredSmtpLeaveDatabaseUntouched() {
        service.suspendApplications();
        service.deliverNotices();

        verifyNoInteractions(mapper);
    }

    @Test
    void sendsConfiguredPlainTextToOwnerAndMarksNoticeSent() throws Exception {
        var notice = notice(1);
        when(mapper.claimNotice()).thenReturn(notice, (Notice) null);
        template();

        service.deliverNotices();

        var order = inOrder(mapper, smtp);
        order.verify(mapper).claimNotice();
        order.verify(smtp).send(null, "owner@example.invalid", "Suspended app$1",
                "A$B: 151/150, 12001/12000 (7 days), #42", null);
        order.verify(mapper).finishNotice(8L, 1, MailStatus.SENT);
    }

    @Test
    void smtpErrorsKeepNoticePendingUntilLastAttempt() throws Exception {
        when(mapper.claimNotice()).thenReturn(notice(1), null, notice(5), null);
        template();
        when(smtp.send(isNull(), anyString(), anyString(), anyString(), isNull()))
                .thenThrow(new MailDeliveryException("Test SMTP outage"));

        service.deliverNotices();
        verify(mapper, never()).finishNotice(anyLong(), anyInt(), any());
        service.deliverNotices();
        verify(mapper).finishNotice(8L, 5, MailStatus.FAILED);
    }

    private void template() {
        when(smtp.available()).thenReturn(true);
        when(config.text("banReplaySuspension", "emailSubject")).thenReturn("Suspended {{appId}}");
        when(config.text("banReplaySuspension", "emailBody"))
                .thenReturn("{{recipientName}}: {{replayBatches}}/{{batchLimit}}, {{replayLogs}}/{{logLimit}} ({{windowDays}} days), #{{applicationId}}");
    }

    private Notice notice(int attempt) {
        return new Notice(8L, 42L, "app$1", "owner@example.invalid", "A$B",
                OffsetDateTime.parse("2026-01-20T10:05:00Z"), 151L, 12_001L, 150L, 12_000L, attempt);
    }
}
