package com.ghostchu.btn.sparkle.service.mail;

import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.MailSecurity;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.SenderIdentity;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.Smtp;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmtpMailService {
    private final MailSettingsService settingsService;

    public boolean available() {
        return settingsService.smtpConfigured(settingsService.get());
    }

    public String send(
            SenderIdentity senderIdentity,
            String recipient,
            String subject,
            String text,
            UUID replyToken
    ) throws MailDeliveryException {
        MailRuntimeSettings settings = settingsService.get();
        if (!settingsService.smtpConfigured(settings)) {
            throw new MailDeliveryException("SMTP is disabled or not fully configured");
        }
        String fromAddress = senderIdentity == null
                || senderIdentity.address() == null
                || senderIdentity.address().isBlank()
                ? settingsService.systemSenderAddress(settings)
                : senderIdentity.address();
        String fromName = senderIdentity == null
                || senderIdentity.name() == null
                || senderIdentity.name().isBlank()
                ? settingsService.systemSenderName(settings)
                : senderIdentity.name();
        if (!MailSettingsService.validAddress(fromAddress)) {
            throw new MailDeliveryException("SMTP sender address is invalid");
        }
        InternetAddress recipientAddress = parseSingleRecipient(recipient);
        JavaMailSenderImpl mailSender = createSender(settings.smtp());
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(
                    mimeMessage,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(new InternetAddress(
                    fromAddress,
                    safeHeader(fromName),
                    StandardCharsets.UTF_8.name()
            ));
            helper.setTo(recipientAddress);
            helper.setSubject(safeHeader(subject));
            helper.setText(text == null ? "" : text, false);
            mimeMessage.setHeader("X-Mailer", "Sparkle BTN");

            String messageId = null;
            if (replyToken != null && settingsService.replyByEmailAvailable(settings)) {
                String replyAddress = settingsService.replyAddress(settings);
                if (!MailSettingsService.validAddress(replyAddress)) {
                    throw new MailDeliveryException("POP3 reply address is invalid");
                }
                helper.setReplyTo(new InternetAddress(replyAddress));
                messageId = messageId(replyToken, fromAddress);
                mimeMessage.setHeader("Message-ID", messageId);
                mimeMessage.setHeader("X-Sparkle-Reply-Token", replyToken.toString());
            }
            mailSender.send(mimeMessage);
            return messageId;
        } catch (MailDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MailDeliveryException(
                    "SMTP delivery failed: " + safeErrorMessage(exception),
                    exception
            );
        }
    }

    private JavaMailSenderImpl createSender(Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        sender.setUsername(emptyToNull(smtp.username()));
        sender.setPassword(emptyToNull(smtp.password()));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean ssl = smtp.security() == MailSecurity.SSL;
        sender.setProtocol(ssl ? "smtps" : "smtp");

        Properties properties = new Properties();
        String prefix = ssl ? "mail.smtps." : "mail.smtp.";
        properties.setProperty(prefix + "auth", String.valueOf(smtp.auth()));
        properties.setProperty(prefix + "connectiontimeout",
                String.valueOf(smtp.connectionTimeoutSeconds() * 1_000L));
        properties.setProperty(prefix + "timeout",
                String.valueOf(smtp.readTimeoutSeconds() * 1_000L));
        properties.setProperty(prefix + "writetimeout",
                String.valueOf(smtp.writeTimeoutSeconds() * 1_000L));
        if (ssl) {
            properties.setProperty("mail.smtps.ssl.enable", "true");
            properties.setProperty("mail.smtps.ssl.checkserveridentity", "true");
        } else if (smtp.security() == MailSecurity.STARTTLS) {
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
        }
        sender.setJavaMailProperties(properties);
        return sender;
    }

    private InternetAddress parseSingleRecipient(String recipient) throws MailDeliveryException {
        try {
            InternetAddress[] addresses = InternetAddress.parse(recipient, true);
            if (addresses.length != 1) {
                throw new MailDeliveryException("Exactly one email recipient is required");
            }
            addresses[0].validate();
            return addresses[0];
        } catch (MailDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MailDeliveryException("Email recipient is invalid", exception);
        }
    }

    private String messageId(UUID replyToken, String fromAddress) {
        String domain = "sparkle.invalid";
        int separator = fromAddress == null ? -1 : fromAddress.lastIndexOf('@');
        if (separator >= 0 && separator + 1 < fromAddress.length()) {
            String candidate = fromAddress.substring(separator + 1)
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9.-]", "");
            if (!candidate.isBlank()) {
                domain = candidate;
            }
        }
        return "<sparkle." + replyToken + "@" + domain + ">";
    }

    public static UUID replyTokenFromMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        var matcher = java.util.regex.Pattern.compile(
                "(?i)<sparkle\\.([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})@"
        ).matcher(messageId);
        if (!matcher.find()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String safeHeader(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").trim();
    }

    private String safeErrorMessage(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
