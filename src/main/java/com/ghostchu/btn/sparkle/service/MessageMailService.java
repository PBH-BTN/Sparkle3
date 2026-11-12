package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageMailService {
    private final UserMessageMapper messageMapper;
    private final IUserService userService;
    private final JavaMailSender mailSender;
    private final BtnRuntimeConfigService btnConfigService;

    public void deliverDispatch(UUID dispatchId) {
        List<UserMessage> messages = messageMapper.selectList(
                new QueryWrapper<UserMessage>()
                        .eq("dispatch_id", dispatchId)
                        .eq("email_requested", true)
                        .eq("email_status", "PENDING")
                        .orderByAsc("id")
        );
        for (UserMessage message : messages) {
            User recipient = userService.getById(message.getRecipientUserId());
            deliver(new MailRow(
                    message.getId(),
                    message.getSenderKind(),
                    message.getSenderDisplayName(),
                    message.getSubject(),
                    message.getContent(),
                    recipient == null ? null : recipient.getEmail(),
                    recipient == null ? null : recipient.getNickname()
            ));
        }
    }

    private void deliver(MailRow row) {
        String recipient = row.recipientEmail();
        if (recipient == null
                || recipient.isBlank()
                || recipient.toLowerCase(Locale.ROOT).endsWith("@sparkle.system")) {
            mark(row.id(), "SKIPPED", "No deliverable recipient email", false);
            return;
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(recipient, true);
            if (parsed.length != 1) {
                throw new IllegalArgumentException("Exactly one recipient is required");
            }
            parsed[0].validate();

            String config = "SYSTEM".equalsIgnoreCase(row.senderKind())
                    ? "versionAlert"
                    : "userContactEmail";
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(new InternetAddress(
                    btnConfigService.text(config, "fromAddress"),
                    btnConfigService.text(config, "fromName")
            ));
            helper.setTo(parsed[0]);
            helper.setSubject("[Sparkle BTN] " + stripLineBreaks(row.subject()));
            helper.setText(body(row), false);
            mailSender.send(mimeMessage);
            mark(row.id(), "SENT", null, true);
        } catch (Exception exception) {
            log.warn("Unable to deliver email notification for message {}: {}",
                    row.id(), exception.getMessage());
            mark(row.id(), "FAILED", truncate(exception.getMessage(), 2_000), false);
        }
    }

    private String body(MailRow row) {
        String recipient = row.recipientNickname() == null || row.recipientNickname().isBlank()
                ? "Sparkle BTN 用户"
                : row.recipientNickname().trim();
        return """
                %s：

                您收到一条来自 %s 的 Sparkle BTN 站内信：

                %s

                ---
                %s

                这是一封由 Sparkle BTN 自动发送的提醒邮件，请勿直接回复。
                您可以在 Sparkle BTN 的收件箱中查看完整消息并管理通知提醒设置。
                """.formatted(
                recipient,
                row.senderDisplayName(),
                row.content(),
                row.subject()
        );
    }

    private void mark(long id, String status, String error, boolean sentAt) {
        LambdaUpdateWrapper<UserMessage> update =
                new LambdaUpdateWrapper<UserMessage>()
                        .set(UserMessage::getEmailStatus, status)
                        .set(UserMessage::getEmailError, truncate(error, 2_000))
                        .eq(UserMessage::getId, id)
                        .eq(UserMessage::getEmailStatus, "PENDING");
        if (sentAt) {
            update.set(UserMessage::getEmailSentAt, java.time.OffsetDateTime.now());
        }
        messageMapper.update(null, update);
    }

    private String stripLineBreaks(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record MailRow(
            long id,
            String senderKind,
            String senderDisplayName,
            String subject,
            String content,
            String recipientEmail,
            String recipientNickname
    ) {
    }
}
