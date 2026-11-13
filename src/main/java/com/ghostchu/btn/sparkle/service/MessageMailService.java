package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.service.mail.MailDeliveryException;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings;
import com.ghostchu.btn.sparkle.service.mail.MailSettingsService;
import com.ghostchu.btn.sparkle.service.mail.SmtpMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageMailService {
    private final MailUnsubscribeService unsubscribeService;
    private final UserMessageMapper messageMapper;
    private final IUserService userService;
    private final SmtpMailService smtpMailService;
    private final MailSettingsService mailSettingsService;

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
                    message.getRecipientUserId(),
                    message.getSenderKind(),
                    message.getSenderUserId(),
                    message.getCategory(),
                    message.getSenderDisplayName(),
                    message.getSubject(),
                    message.getContent(),
                    recipient == null ? null : recipient.getEmail(),
                    recipient == null ? null : recipient.getNickname(),
                    message.getReplyToken()
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
        if (!smtpMailService.available()) {
            mark(row.id(), "SKIPPED", "SMTP is disabled or not fully configured", false);
            return;
        }
        try {
            MailRuntimeSettings settings = mailSettingsService.get();
            boolean system = "SYSTEM".equalsIgnoreCase(row.senderKind());
            var senderIdentity = system
                    ? settings.systemSender()
                    : settings.userSender();
            boolean canReply = row.replyToken() != null
                    && row.senderUserId() != null
                    && "DIRECT_MESSAGE".equalsIgnoreCase(row.category())
                    && mailSettingsService.replyByEmailAvailable(settings);
            smtpMailService.send(
                    senderIdentity,
                    recipient,
                    "[Sparkle BTN] " + stripLineBreaks(row.subject()),
                    body(row, canReply),
                    canReply ? row.replyToken() : null,
                    NotificationCategory.parse(row.category()).map(category ->
                            unsubscribeService.link(row.recipientUserId(), recipient, category)).orElse(null)
            );
            mark(row.id(), "SENT", null, true);
        } catch (MailDeliveryException exception) {
            log.warn("Unable to deliver email notification for message {}: {}",
                    row.id(), exception.getMessage());
            mark(row.id(), "FAILED", truncate(exception.getMessage(), 2_000), false);
        } catch (Exception exception) {
            log.warn("Unable to deliver email notification for message {}: {}",
                    row.id(), exception.getMessage());
            mark(row.id(), "FAILED", truncate(exception.getMessage(), 2_000), false);
        }
    }

    private String body(MailRow row, boolean canReply) {
        String recipient = row.recipientNickname() == null || row.recipientNickname().isBlank()
                ? "Sparkle BTN 用户"
                : row.recipientNickname().trim();
        String replyHelp = canReply
                ? "您可以直接回复此邮件，回复内容会发送回原发件人的 Sparkle BTN 站内信。"
                : "这是一封由 Sparkle BTN 自动发送的提醒邮件，请勿直接回复。";
        String inboxHelp = canReply
                ? "回复地址必须与您当前账号绑定的邮箱一致；您也可以登录 Sparkle BTN 收件箱查看完整消息。"
                : "您可以在 Sparkle BTN 的收件箱中查看完整消息并管理通知提醒设置。";
        return """
                %s：

                您收到一条来自 %s 的 Sparkle BTN 站内信：

                %s

                ---
                %s

                %s
                %s
                """.formatted(
                recipient,
                row.senderDisplayName(),
                row.content(),
                row.subject(),
                replyHelp,
                inboxHelp
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
            long recipientUserId,
            String senderKind,
            Long senderUserId,
            String category,
            String senderDisplayName,
            String subject,
            String content,
            String recipientEmail,
            String recipientNickname,
            UUID replyToken
    ) {
    }
}
