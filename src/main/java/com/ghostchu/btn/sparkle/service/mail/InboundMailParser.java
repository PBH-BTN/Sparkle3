package com.ghostchu.btn.sparkle.service.mail;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Component
@Slf4j
public class InboundMailParser {
    private static final int MAX_CONTENT_LENGTH = 20_000;

    public @Nullable ParsedMessage parse(MimeMessage message) {
        try {
            String from = fromAddress(message);
            if (from == null) {
                return null;
            }
            String autoSubmitted = firstHeader(message, "Auto-Submitted");
            String precedence = firstHeader(message, "Precedence");
            boolean automatic = autoSubmitted != null
                    && !"no".equalsIgnoreCase(autoSubmitted)
                    || precedence != null
                    && switch (precedence.toLowerCase(Locale.ROOT)) {
                        case "bulk", "junk", "list", "auto_reply" -> true;
                        default -> false;
                    };
            UUID replyToken = replyToken(message);
            String subject = safeSubject(message.getSubject());
            String content = replyContent(message);
            return new ParsedMessage(
                    from,
                    replyToken,
                    trimToNull(firstHeader(message, "Message-ID"), 2_000),
                    subject,
                    content,
                    automatic,
                    receivedAt(message),
                    message.getSize()
            );
        } catch (Exception exception) {
            log.warn("Unable to parse an inbound mail message: {}", exception.getMessage());
            return null;
        }
    }

    public static UUID replyToken(MimeMessage message) throws MessagingException {
        String inReplyTo = firstHeader(message, "In-Reply-To");
        UUID token = SmtpMailService.replyTokenFromMessageId(inReplyTo);
        if (token != null) {
            return token;
        }
        String references = firstHeader(message, "References");
        if (references == null) {
            return null;
        }
        for (String candidate : references.split("\\s+")) {
            token = SmtpMailService.replyTokenFromMessageId(candidate);
            if (token != null) {
                return token;
            }
        }
        return null;
    }

    private String fromAddress(MimeMessage message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null || from.length != 1 || !(from[0] instanceof InternetAddress address)) {
            return null;
        }
        String value = address.getAddress();
        if (!MailSettingsService.validAddress(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String replyContent(MimeMessage message) throws MessagingException, IOException {
        String plain = extractText(message, true);
        String content = plain == null || plain.isBlank()
                ? stripHtml(extractText(message, false))
                : plain;
        content = normalizeNewlines(content);
        content = stripQuotedContent(content);
        return trimToNull(content, MAX_CONTENT_LENGTH);
    }

    private String extractText(Part part, boolean plainText) throws MessagingException, IOException {
        if (isAttachment(part)) {
            return null;
        }
        if (part.isMimeType("text/plain") && plainText) {
            Object content = part.getContent();
            return content == null ? null : String.valueOf(content);
        }
        if (part.isMimeType("text/html") && !plainText) {
            Object content = part.getContent();
            return content == null ? null : String.valueOf(content);
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int index = 0; index < multipart.getCount(); index++) {
                    String candidate = extractText(multipart.getBodyPart(index), plainText);
                    if (candidate != null && !candidate.isBlank()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isAttachment(Part part) throws MessagingException {
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return true;
        }
        return disposition != null && part.getFileName() != null;
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        String plain = html
                .replaceAll("(?is)<(script|style|head)\\b.*?</\\1\\s*>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return plain
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String stripQuotedContent(String content) {
        if (content == null) {
            return null;
        }
        StringBuilder kept = new StringBuilder();
        boolean hasContent = false;
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (isQuoteBoundary(trimmed)) {
                break;
            }
            kept.append(line).append('\n');
            if (!trimmed.isBlank()) {
                hasContent = true;
            }
        }
        return hasContent ? kept.toString().trim() : null;
    }

    private boolean isQuoteBoundary(String line) {
        if (line.startsWith(">")) {
            return true;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.matches("^-{2,}\\s*original message\\s*-{2,}$")
                || lower.matches("^on\\s.+\\swrote:$")
                || lower.matches("^from:\\s.+$")
                || line.matches("^在\\s.+写道[:：]$")
                || lower.matches("^-{2,}\\s*原始邮件\\s*-{2,}$");
    }

    private String normalizeNewlines(String value) {
        return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private OffsetDateTime receivedAt(MimeMessage message) {
        try {
            var received = message.getReceivedDate();
            if (received != null) {
                return received.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            }
        } catch (MessagingException ignored) {
            // Fall through to the processing timestamp.
        }
        return OffsetDateTime.now();
    }

    private String safeSubject(String value) {
        return trimToNull(
                value == null ? "" : value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").trim(),
                500
        );
    }

    private static String firstHeader(MimeMessage message, String name)
            throws MessagingException {
        String[] values = message.getHeader(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private String trimToNull(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength);
    }

    public record ParsedMessage(
            String fromAddress,
            UUID replyToken,
            String messageId,
            String subject,
            String content,
            boolean automatic,
            OffsetDateTime receivedAt,
            int size
    ) {
    }
}
