package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.IngestionStatusView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.MailSettingsUpdate;
import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.MailSettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.Pop3View;
import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.SenderView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.SmtpView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.TestResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.MailAdminModels.TestSmtpRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.mail.MailDeliveryException;
import com.ghostchu.btn.sparkle.service.mail.MailIngestionStatus;
import com.ghostchu.btn.sparkle.service.mail.MailReplyIngestionService;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.SenderIdentity;
import com.ghostchu.btn.sparkle.service.mail.MailSettingsService;
import com.ghostchu.btn.sparkle.service.mail.SmtpMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/mail")
@RequiredArgsConstructor
public class AdminMailSettingsController {
    private final MailSettingsService settingsService;
    private final MailReplyIngestionService ingestionService;
    private final SmtpMailService smtpMailService;

    @GetMapping("/settings")
    public MailSettingsView get() {
        return view(settingsService.get(), ingestionService.status());
    }

    @PutMapping("/settings")
    public MailSettingsView update(
            @RequestBody MailSettingsUpdate request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mail settings are required");
        }
        MailRuntimeSettings updated = settingsService.update(
                new MailSettingsService.UpdateRequest(
                        request.smtpEnabled(),
                        request.smtpHost(),
                        request.smtpPort(),
                        request.smtpUsername(),
                        blankToNull(request.smtpPassword()),
                        request.clearSmtpPassword(),
                        request.smtpSecurity(),
                        request.smtpAuth(),
                        request.smtpConnectionTimeoutSeconds(),
                        request.smtpReadTimeoutSeconds(),
                        request.smtpWriteTimeoutSeconds(),
                        request.smtpFromAddress(),
                        request.smtpFromName(),
                        request.pop3Enabled(),
                        request.pop3Host(),
                        request.pop3Port(),
                        request.pop3Username(),
                        blankToNull(request.pop3Password()),
                        request.clearPop3Password(),
                        request.pop3Security(),
                        request.pop3ReplyAddress(),
                        request.pop3PollIntervalSeconds(),
                        request.pop3ConnectionTimeoutSeconds(),
                        request.pop3MaxMessagesPerPoll(),
                        sender(request.systemSender()),
                        sender(request.userSender())
                ),
                principal == null ? null : principal.getUserId()
        );
        return view(updated, ingestionService.status());
    }

    @PostMapping("/settings/test-smtp")
    public TestResult testSmtp(@RequestBody TestSmtpRequest request)
            throws BusinessException {
        if (request == null || request.recipient() == null || request.recipient().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Test recipient is required");
        }
        try {
            MailRuntimeSettings settings = settingsService.get();
            smtpMailService.send(
                    settings.systemSender(),
                    request.recipient(),
                    "[Sparkle BTN] SMTP configuration test",
                    "This message confirms that the backend-managed SMTP configuration is working.",
                    null
            );
            return new TestResult(true, "Test email sent");
        } catch (MailDeliveryException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    @PostMapping("/settings/test-pop3")
    public TestResult testPop3() throws BusinessException {
        ingestionService.testConnection();
        return new TestResult(true, "POP3 connection succeeded");
    }

    @PostMapping("/settings/scan")
    public IngestionStatusView scan() throws BusinessException {
        return map(ingestionService.scanNow(true));
    }

    private MailSettingsView view(MailRuntimeSettings settings, MailIngestionStatus status) {
        return new MailSettingsView(
                new SmtpView(
                        settings.smtp().enabled(),
                        settings.smtp().host(),
                        settings.smtp().port(),
                        settings.smtp().username(),
                        settings.smtp().security(),
                        settings.smtp().auth(),
                        settings.smtp().connectionTimeoutSeconds(),
                        settings.smtp().readTimeoutSeconds(),
                        settings.smtp().writeTimeoutSeconds(),
                        settings.smtp().fromAddress(),
                        settings.smtp().fromName()
                ),
                new Pop3View(
                        settings.pop3().enabled(),
                        settings.pop3().host(),
                        settings.pop3().port(),
                        settings.pop3().username(),
                        settings.pop3().security(),
                        settings.pop3().replyAddress(),
                        settings.pop3().pollIntervalSeconds(),
                        settings.pop3().connectionTimeoutSeconds(),
                        settings.pop3().maxMessagesPerPoll()
                ),
                new SenderView(
                        settings.systemSender().address(),
                        settings.systemSender().name()
                ),
                new SenderView(
                        settings.userSender().address(),
                        settings.userSender().name()
                ),
                !settings.smtp().password().isBlank(),
                !settings.pop3().password().isBlank(),
                map(status)
        );
    }

    private IngestionStatusView map(MailIngestionStatus status) {
        return new IngestionStatusView(
                status.running(),
                status.lastScanAt(),
                status.lastSuccessAt(),
                status.nextScanAt(),
                status.lastImported(),
                status.lastSkipped(),
                status.lastFailed(),
                status.totalImported(),
                status.lastError()
        );
    }

    private SenderIdentity sender(SenderView sender) {
        return sender == null ? null : new SenderIdentity(sender.address(), sender.name());
    }

    private String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
