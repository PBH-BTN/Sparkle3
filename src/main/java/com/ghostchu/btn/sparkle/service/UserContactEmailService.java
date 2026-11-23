package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ContactEmailRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ContactEmailResult;
import com.ghostchu.btn.sparkle.entity.UserContactEmailDelivery;
import com.ghostchu.btn.sparkle.mapper.UserContactEmailDeliveryMapper;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.mail.MailDeliveryException;
import com.ghostchu.btn.sparkle.service.mail.MailSettingsService;
import com.ghostchu.btn.sparkle.service.mail.PlainTextMailTemplate;
import com.ghostchu.btn.sparkle.service.mail.SmtpMailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class UserContactEmailService {
    private static final int DAILY_LIMIT = 15;

    private final UserContactEmailDeliveryMapper deliveryMapper;
    private final UserPublicProfileService publicProfileService;
    private final AdminConfigService adminConfigService;
    private final SmtpMailService smtpMailService;
    private final MailSettingsService mailSettingsService;
    private final UserContactEmailReservationService reservations;

    public UserContactEmailService(
            UserContactEmailDeliveryMapper deliveryMapper,
            UserPublicProfileService publicProfileService,
            AdminConfigService adminConfigService,
            SmtpMailService smtpMailService,
            MailSettingsService mailSettingsService,
            UserContactEmailReservationService reservations
    ) {
        this.deliveryMapper = deliveryMapper;
        this.publicProfileService = publicProfileService;
        this.adminConfigService = adminConfigService;
        this.smtpMailService = smtpMailService;
        this.mailSettingsService = mailSettingsService;
        this.reservations = reservations;
    }

    public ContactEmailResult send(
            long senderUserId,
            long recipientUserId,
            ContactEmailRequest request,
            String senderIp,
            String senderUserAgent
    ) throws BusinessException {
        User sender = publicProfileService.requireUser(senderUserId);
        User recipient = publicProfileService.requireUser(recipientUserId);
        if (!publicProfileService.canContactEmail(sender, recipient)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "This user does not accept contact emails");
        }
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email content is required");
        }

        String subject;
        String content;
        try {
            subject = PlainTextEmailSanitizer.subject(request.subject());
            content = PlainTextEmailSanitizer.content(request.content());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Email title or content is too long");
        }
        if (subject.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email title is required");
        }
        if (content.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email content is required");
        }

        RuntimeConfigView config = adminConfigService.get();
        String renderedSubject = PlainTextMailTemplate.render(
                config.userContactEmailSubjectTemplate(),
                Map.of(
                        "title", subject,
                        "senderName", displayName(sender),
                        "senderId", String.valueOf(sender.getId()),
                        "recipientName", displayName(recipient),
                        "recipientId", String.valueOf(recipient.getId())
                )
        ).replaceAll("[\\r\\n]+", " ").trim();
        String renderedBody = PlainTextMailTemplate.render(
                config.userContactEmailBodyTemplate(),
                Map.of(
                        "title", subject,
                        "content", content,
                        "senderName", displayName(sender),
                        "senderId", String.valueOf(sender.getId()),
                        "recipientName", displayName(recipient),
                        "recipientId", String.valueOf(recipient.getId())
                )
        );

        String safeIp = truncate(senderIp, 64);
        if (safeIp != null && safeIp.isBlank()) {
            safeIp = null;
        }
        String safeUserAgent = truncate(sanitizeHeader(senderUserAgent), 500);
        var delivery = new UserContactEmailDelivery()
                .setSenderUserId(sender.getId())
                .setSenderNickname(sender.getNickname())
                .setSenderEmail(sender.getEmail())
                .setRecipientUserId(recipient.getId())
                .setRecipientNickname(recipient.getNickname())
                .setRecipientEmail(recipient.getEmail())
                .setSubject(truncate(renderedSubject, 255))
                .setSenderIp(safeIp)
                .setSenderUserAgent(safeUserAgent)
                .setSentAt(java.time.OffsetDateTime.now());
        int remaining = reservations.reserve(delivery, DAILY_LIMIT);
        try {
            sendPlainTextEmail(recipient.getEmail(), renderedSubject, renderedBody);
        } catch (BusinessException failure) {
            deliveryMapper.deleteById(delivery.getId());
            throw failure;
        }
        log.info("User {} sent a contact email to user {} from {}",
                sender.getId(), recipient.getId(), safeIp);
        return new ContactEmailResult(true, remaining);
    }

    private void sendPlainTextEmail(String recipient, String subject, String content)
            throws BusinessException {
        try {
            smtpMailService.send(
                    mailSettingsService.get().userSender(),
                    recipient,
                    subject,
                    content,
                    null
            );
        } catch (MailDeliveryException exception) {
            log.warn("Unable to send user contact email: {}", exception.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to send contact email");
        }
    }

    private String displayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return sanitizeHeader(user.getNickname());
        }
        return "UID " + user.getId();
    }

    private String sanitizeHeader(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
