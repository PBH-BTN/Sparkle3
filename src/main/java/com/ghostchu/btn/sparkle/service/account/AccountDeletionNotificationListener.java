package com.ghostchu.btn.sparkle.service.account;

import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionNotificationListener {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    private static final String DISCLAIMER =
            "\n\n此邮件由 Sparkle BTN 自动发送。如非本人操作，请立即联系系统管理员。";

    private final JavaMailSender mailSender;
    private final BtnRuntimeConfigService btnConfigService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void send(AccountDeletionNotificationEvent event) {
        if (event == null || event.recipientEmail() == null || event.recipientEmail().isBlank()) {
            log.warn("Cannot send account deletion notification for user {}: recipient is missing",
                    event == null ? null : event.userId());
            return;
        }
        try {
            InternetAddress[] recipients = InternetAddress.parse(event.recipientEmail(), true);
            if (recipients.length != 1) {
                throw new IllegalArgumentException("Exactly one recipient is required");
            }
            recipients[0].validate();

            String subject = subject(event.kind());
            String body = body(event);
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(new InternetAddress(
                    btnConfigService.text("versionAlert", "fromAddress"),
                    btnConfigService.text("versionAlert", "fromName")
            ));
            helper.setTo(recipients[0]);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(mimeMessage);
            log.info("Sent {} account deletion notification for user {}",
                    event.kind(), event.userId());
        } catch (Exception exception) {
            log.error("Unable to send {} account deletion notification for user {}: {}",
                    event.kind(), event.userId(), exception.getMessage(), exception);
        }
    }

    static String subject(AccountDeletionNotificationEvent.Kind kind) {
        return switch (kind) {
            case STARTED -> "Sparkle BTN 账号注销申请已开始";
            case CANCELLED -> "Sparkle BTN 账号注销申请已取消";
            case COMPLETED -> "Sparkle BTN 账号注销已完成";
        };
    }

    static String body(AccountDeletionNotificationEvent event) {
        String user = displayName(event.nickname(), event.userId());
        String content = switch (event.kind()) {
            case STARTED -> """
                    %s（UID %d）：

                    您的 Sparkle BTN 账号注销申请已开始。

                    注销将在 7 天冷静期结束后完成，预计完成时间：%s。
                    冷静期内请勿登录账号或使用任何用户应用。发生登录尝试或用户应用活动将立即取消本次注销，并发送通知。
                    冷静期结束后，所有用户应用将停用，账号个人标识将匿名化，且此操作不可撤销。
                    """.formatted(
                    user,
                    event.userId(),
                    formatTime(event.coolingEndsAt())
            );
            case CANCELLED -> """
                    %s（UID %d）：

                    您的 Sparkle BTN 账号注销申请已取消。

                    原因：%s
                    申请时间：%s
                    原计划完成时间：%s

                    账号仍可正常使用。如仍需注销，请重新发起申请并完成 GitHub 再次授权。
                    """.formatted(
                    user,
                    event.userId(),
                    event.cancellationDetail() == null || event.cancellationDetail().isBlank()
                            ? "注销流程被取消"
                            : event.cancellationDetail(),
                    formatTime(event.requestedAt()),
                    formatTime(event.coolingEndsAt())
            );
            case COMPLETED -> """
                    %s（UID %d）：

                    您的 Sparkle BTN 账号注销已完成。

                    所有用户应用均已停用，个人标识、GitHub 绑定信息与隐私设置均已按注销规则处理。
                    此账号的匿名化资料将保留，但无法再用于登录。完成时间：%s。
                    """.formatted(
                    user,
                    event.userId(),
                    formatTime(event.completedAt())
            );
        };
        return content + DISCLAIMER;
    }

    private static String displayName(String nickname, long userId) {
        if (nickname == null || nickname.isBlank()) {
            return "UID " + userId;
        }
        return nickname.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").trim();
    }

    private static String formatTime(java.time.OffsetDateTime value) {
        return value == null ? "未知" : DATE_FORMAT.format(value);
    }
}
