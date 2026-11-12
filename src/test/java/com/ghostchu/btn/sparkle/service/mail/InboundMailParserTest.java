package com.ghostchu.btn.sparkle.service.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundMailParserTest {
    private final InboundMailParser parser = new InboundMailParser();

    @Test
    void extractsReplyTokenAndRemovesQuotedContent() throws Exception {
        UUID token = UUID.randomUUID();
        MimeMessage message = message();
        message.setFrom(new InternetAddress("Recipient@Example.com"));
        message.setRecipients(MimeMessage.RecipientType.TO, "inbox@example.com");
        message.setSubject("Re: A station message");
        message.setHeader("Message-ID", "<reply@example.com>");
        message.setHeader("In-Reply-To",
                "<sparkle." + token + "@sparkle.example.com>");
        message.setText("""
                Thanks, this reply came from email.

                On yesterday, Sparkle wrote:
                > Original content
                """, "text/plain; charset=UTF-8");
        message.saveChanges();

        InboundMailParser.ParsedMessage parsed = parser.parse(message);

        assertNotNull(parsed);
        assertEquals(token, parsed.replyToken());
        assertEquals("recipient@example.com", parsed.fromAddress());
        assertEquals("Thanks, this reply came from email.", parsed.content());
        assertFalse(parsed.automatic());
    }

    @Test
    void fallsBackToHtmlTextAndDetectsAutomaticReplies() throws Exception {
        UUID token = UUID.randomUUID();
        MimeMessage message = message();
        message.setFrom(new InternetAddress("recipient@example.com"));
        message.setSubject("Re: test");
        message.setHeader("References",
                "<first@example.com> <sparkle." + token + "@example.com>");
        message.setHeader("Auto-Submitted", "auto-replied");
        message.setContent(
                "<html><body><p>Hello <strong>from HTML</strong></p></body></html>",
                "text/html; charset=UTF-8"
        );
        message.saveChanges();

        InboundMailParser.ParsedMessage parsed = parser.parse(message);

        assertNotNull(parsed);
        assertEquals(token, parsed.replyToken());
        assertEquals("Hello from HTML", parsed.content());
        assertTrue(parsed.automatic());
    }

    @Test
    void ignoresMessagesWithoutReplyToken() throws Exception {
        MimeMessage message = message();
        message.setFrom(new InternetAddress("recipient@example.com"));
        message.setSubject("Ordinary mail");
        message.setText("No token here.");
        message.saveChanges();

        InboundMailParser.ParsedMessage parsed = parser.parse(message);

        assertNotNull(parsed);
        assertNull(parsed.replyToken());
    }

    @Test
    void parsesReplyTokenFromGeneratedMessageId() {
        UUID token = UUID.randomUUID();

        assertEquals(
                token,
                SmtpMailService.replyTokenFromMessageId(
                        "<sparkle." + token + "@sparkle.example.com>"
                )
        );
        assertNull(SmtpMailService.replyTokenFromMessageId("<other@example.com>"));
    }

    private MimeMessage message() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }
}
