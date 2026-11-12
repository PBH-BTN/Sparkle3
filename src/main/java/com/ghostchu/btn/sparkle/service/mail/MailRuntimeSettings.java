package com.ghostchu.btn.sparkle.service.mail;

public record MailRuntimeSettings(
        Smtp smtp,
        Pop3 pop3,
        SenderIdentity systemSender,
        SenderIdentity userSender
) {
    public record Smtp(
            boolean enabled,
            String host,
            int port,
            String username,
            String password,
            MailSecurity security,
            boolean auth,
            int connectionTimeoutSeconds,
            int readTimeoutSeconds,
            int writeTimeoutSeconds,
            String fromAddress,
            String fromName
    ) {
    }

    public record Pop3(
            boolean enabled,
            String host,
            int port,
            String username,
            String password,
            MailSecurity security,
            String replyAddress,
            int pollIntervalSeconds,
            int connectionTimeoutSeconds,
            int maxMessagesPerPoll
    ) {
    }

    public record SenderIdentity(String address, String name) {
    }

    public enum MailSecurity {
        NONE,
        STARTTLS,
        SSL
    }
}
