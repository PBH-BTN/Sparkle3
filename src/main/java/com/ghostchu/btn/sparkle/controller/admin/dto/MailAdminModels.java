package com.ghostchu.btn.sparkle.controller.admin.dto;

import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.MailSecurity;

import java.time.OffsetDateTime;

public final class MailAdminModels {
    private MailAdminModels() {
    }

    public record MailSettingsView(
            SmtpView smtp,
            Pop3View pop3,
            SenderView systemSender,
            SenderView userSender,
            boolean smtpPasswordConfigured,
            boolean pop3PasswordConfigured,
            IngestionStatusView ingestion
    ) {
    }

    public record SmtpView(
            boolean enabled,
            String host,
            int port,
            String username,
            MailSecurity security,
            boolean auth,
            int connectionTimeoutSeconds,
            int readTimeoutSeconds,
            int writeTimeoutSeconds,
            String fromAddress,
            String fromName
    ) {
    }

    public record Pop3View(
            boolean enabled,
            String host,
            int port,
            String username,
            MailSecurity security,
            String replyAddress,
            int pollIntervalSeconds,
            int connectionTimeoutSeconds,
            int maxMessagesPerPoll
    ) {
    }

    public record SenderView(String address, String name) {
    }

    public record IngestionStatusView(
            boolean running,
            OffsetDateTime lastScanAt,
            OffsetDateTime lastSuccessAt,
            OffsetDateTime nextScanAt,
            int lastImported,
            int lastSkipped,
            int lastFailed,
            long totalImported,
            String lastError
    ) {
    }

    public record MailSettingsUpdate(
            boolean smtpEnabled,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            boolean clearSmtpPassword,
            MailSecurity smtpSecurity,
            boolean smtpAuth,
            int smtpConnectionTimeoutSeconds,
            int smtpReadTimeoutSeconds,
            int smtpWriteTimeoutSeconds,
            String smtpFromAddress,
            String smtpFromName,
            boolean pop3Enabled,
            String pop3Host,
            int pop3Port,
            String pop3Username,
            String pop3Password,
            boolean clearPop3Password,
            MailSecurity pop3Security,
            String pop3ReplyAddress,
            int pop3PollIntervalSeconds,
            int pop3ConnectionTimeoutSeconds,
            int pop3MaxMessagesPerPoll,
            SenderView systemSender,
            SenderView userSender
    ) {
    }

    public record TestSmtpRequest(String recipient) {
    }

    public record TestResult(boolean success, String message) {
    }
}
