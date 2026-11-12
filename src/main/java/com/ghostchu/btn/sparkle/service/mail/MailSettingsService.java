package com.ghostchu.btn.sparkle.service.mail;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.MailSecurity;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.Pop3;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.SenderIdentity;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.Smtp;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class MailSettingsService {
    public static final String SMTP_ENABLED = "sparkle.mail.smtp.enabled";
    public static final String SMTP_HOST = "sparkle.mail.smtp.host";
    public static final String SMTP_PORT = "sparkle.mail.smtp.port";
    public static final String SMTP_USERNAME = "sparkle.mail.smtp.username";
    public static final String SMTP_PASSWORD = "sparkle.mail.smtp.password";
    public static final String SMTP_SECURITY = "sparkle.mail.smtp.security";
    public static final String SMTP_AUTH = "sparkle.mail.smtp.auth";
    public static final String SMTP_CONNECTION_TIMEOUT =
            "sparkle.mail.smtp.connection-timeout-seconds";
    public static final String SMTP_READ_TIMEOUT =
            "sparkle.mail.smtp.read-timeout-seconds";
    public static final String SMTP_WRITE_TIMEOUT =
            "sparkle.mail.smtp.write-timeout-seconds";
    public static final String SMTP_FROM_ADDRESS = "sparkle.mail.smtp.from-address";
    public static final String SMTP_FROM_NAME = "sparkle.mail.smtp.from-name";
    public static final String POP3_ENABLED = "sparkle.mail.pop3.enabled";
    public static final String POP3_HOST = "sparkle.mail.pop3.host";
    public static final String POP3_PORT = "sparkle.mail.pop3.port";
    public static final String POP3_USERNAME = "sparkle.mail.pop3.username";
    public static final String POP3_PASSWORD = "sparkle.mail.pop3.password";
    public static final String POP3_SECURITY = "sparkle.mail.pop3.security";
    public static final String POP3_REPLY_ADDRESS = "sparkle.mail.pop3.reply-address";
    public static final String POP3_POLL_INTERVAL =
            "sparkle.mail.pop3.poll-interval-seconds";
    public static final String POP3_CONNECTION_TIMEOUT =
            "sparkle.mail.pop3.connection-timeout-seconds";
    public static final String POP3_MAX_MESSAGES =
            "sparkle.mail.pop3.max-messages-per-poll";
    public static final String SYSTEM_FROM_ADDRESS = "sparkle.version-alert.from-address";
    public static final String SYSTEM_FROM_NAME = "sparkle.version-alert.from-name";
    public static final String USER_FROM_ADDRESS = "sparkle.user-contact-email.from-address";
    public static final String USER_FROM_NAME = "sparkle.user-contact-email.from-name";

    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final List<String> KEYS = List.of(
            SMTP_ENABLED, SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD,
            SMTP_SECURITY, SMTP_AUTH, SMTP_CONNECTION_TIMEOUT, SMTP_READ_TIMEOUT,
            SMTP_WRITE_TIMEOUT, SMTP_FROM_ADDRESS, SMTP_FROM_NAME,
            POP3_ENABLED, POP3_HOST, POP3_PORT, POP3_USERNAME, POP3_PASSWORD,
            POP3_SECURITY, POP3_REPLY_ADDRESS, POP3_POLL_INTERVAL,
            POP3_CONNECTION_TIMEOUT, POP3_MAX_MESSAGES,
            SYSTEM_FROM_ADDRESS, SYSTEM_FROM_NAME, USER_FROM_ADDRESS, USER_FROM_NAME
    );
    private static final MailRuntimeSettings DEFAULTS = new MailRuntimeSettings(
            new Smtp(
                    false, "", 587, "", "", MailSecurity.STARTTLS, true,
                    10, 15, 15, "", "Sparkle BTN"
            ),
            new Pop3(
                    false, "", 995, "", "", MailSecurity.SSL, "",
                    60, 15, 100
            ),
            new SenderIdentity("", "Sparkle BTN 系统通知"),
            new SenderIdentity("", "Sparkle BTN 用户邮件")
    );

    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;
    private final ApplicationEventPublisher eventPublisher;
    private volatile MailRuntimeSettings cached;
    private volatile long cacheExpiresAt;

    public MailSettingsService(
            DatabaseConfigService databaseConfigService,
            Environment environment,
            ApplicationEventPublisher eventPublisher
    ) {
        this.databaseConfigService = databaseConfigService;
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    public @NotNull MailRuntimeSettings get() {
        long now = System.currentTimeMillis();
        MailRuntimeSettings current = cached;
        if (current != null && now < cacheExpiresAt) {
            return current;
        }
        synchronized (this) {
            current = cached;
            if (current != null && now < cacheExpiresAt) {
                return current;
            }
            current = load();
            cached = current;
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return current;
        }
    }

    @Transactional
    public @NotNull MailRuntimeSettings update(
            @NotNull UpdateRequest request,
            @Nullable Long operatorId
    ) throws BusinessException {
        MailRuntimeSettings current = get();
        String smtpPassword = resolveSecret(
                current.smtp().password(),
                request.smtpPassword(),
                request.clearSmtpPassword()
        );
        String pop3Password = resolveSecret(
                current.pop3().password(),
                request.pop3Password(),
                request.clearPop3Password()
        );
        MailRuntimeSettings normalized = normalize(request, current, smtpPassword, pop3Password);
        validate(normalized);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(SMTP_ENABLED, String.valueOf(normalized.smtp().enabled()));
        values.put(SMTP_HOST, normalized.smtp().host());
        values.put(SMTP_PORT, String.valueOf(normalized.smtp().port()));
        values.put(SMTP_USERNAME, normalized.smtp().username());
        values.put(SMTP_SECURITY, normalized.smtp().security().name());
        values.put(SMTP_AUTH, String.valueOf(normalized.smtp().auth()));
        values.put(SMTP_CONNECTION_TIMEOUT,
                String.valueOf(normalized.smtp().connectionTimeoutSeconds()));
        values.put(SMTP_READ_TIMEOUT,
                String.valueOf(normalized.smtp().readTimeoutSeconds()));
        values.put(SMTP_WRITE_TIMEOUT,
                String.valueOf(normalized.smtp().writeTimeoutSeconds()));
        values.put(SMTP_FROM_ADDRESS, normalized.smtp().fromAddress());
        values.put(SMTP_FROM_NAME, normalized.smtp().fromName());
        values.put(POP3_ENABLED, String.valueOf(normalized.pop3().enabled()));
        values.put(POP3_HOST, normalized.pop3().host());
        values.put(POP3_PORT, String.valueOf(normalized.pop3().port()));
        values.put(POP3_USERNAME, normalized.pop3().username());
        values.put(POP3_SECURITY, normalized.pop3().security().name());
        values.put(POP3_REPLY_ADDRESS, normalized.pop3().replyAddress());
        values.put(POP3_POLL_INTERVAL,
                String.valueOf(normalized.pop3().pollIntervalSeconds()));
        values.put(POP3_CONNECTION_TIMEOUT,
                String.valueOf(normalized.pop3().connectionTimeoutSeconds()));
        values.put(POP3_MAX_MESSAGES,
                String.valueOf(normalized.pop3().maxMessagesPerPoll()));
        values.put(SYSTEM_FROM_ADDRESS, normalized.systemSender().address());
        values.put(SYSTEM_FROM_NAME, normalized.systemSender().name());
        values.put(USER_FROM_ADDRESS, normalized.userSender().address());
        values.put(USER_FROM_NAME, normalized.userSender().name());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            databaseConfigService.update(entry.getKey(), entry.getValue(), operatorId);
        }
        databaseConfigService.updateSensitive(SMTP_PASSWORD, smtpPassword, operatorId);
        databaseConfigService.updateSensitive(POP3_PASSWORD, pop3Password, operatorId);
        cached = normalized;
        cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL.toMillis();
        eventPublisher.publishEvent(new MailSettingsChangedEvent());
        return normalized;
    }

    public void invalidate() {
        cached = null;
        cacheExpiresAt = 0;
        eventPublisher.publishEvent(new MailSettingsChangedEvent());
    }

    public boolean smtpConfigured(@NotNull MailRuntimeSettings settings) {
        Smtp smtp = settings.smtp();
        return smtp.enabled()
                && !smtp.host().isBlank()
                && smtp.port() > 0
                && (!smtp.auth() || !smtp.username().isBlank())
                && (!smtp.auth() || !smtp.password().isBlank())
                && validAddress(systemSenderAddress(settings))
                && validAddress(userSenderAddress(settings));
    }

    public boolean pop3Configured(@NotNull MailRuntimeSettings settings) {
        Pop3 pop3 = settings.pop3();
        return pop3.enabled()
                && !pop3.host().isBlank()
                && pop3.port() > 0
                && !pop3.username().isBlank()
                && !pop3.password().isBlank()
                && validAddress(replyAddress(settings));
    }

    public boolean replyByEmailAvailable(@NotNull MailRuntimeSettings settings) {
        return smtpConfigured(settings) && pop3Configured(settings);
    }

    public String systemSenderAddress(@NotNull MailRuntimeSettings settings) {
        return firstNonBlank(settings.systemSender().address(), settings.smtp().fromAddress());
    }

    public String userSenderAddress(@NotNull MailRuntimeSettings settings) {
        return firstNonBlank(settings.userSender().address(), settings.smtp().fromAddress());
    }

    public String systemSenderName(@NotNull MailRuntimeSettings settings) {
        return firstNonBlank(settings.systemSender().name(), settings.smtp().fromName(), "Sparkle BTN");
    }

    public String userSenderName(@NotNull MailRuntimeSettings settings) {
        return firstNonBlank(settings.userSender().name(), settings.smtp().fromName(), "Sparkle BTN");
    }

    public String replyAddress(@NotNull MailRuntimeSettings settings) {
        return firstNonBlank(settings.pop3().replyAddress(), settings.pop3().username());
    }

    @Transactional
    public boolean migrateLegacySpringMail() throws BusinessException {
        String legacyHost = environment.getProperty("spring.mail.host");
        if (legacyHost == null || legacyHost.isBlank()) {
            return false;
        }
        Map<String, String> currentValues = databaseConfigService.getRawValues(List.of(SMTP_HOST));
        String currentHost = currentValues.get(SMTP_HOST);
        if (currentHost != null && !currentHost.isBlank()) {
            return false;
        }
        MailRuntimeSettings current = get();
        String protocol = environment.getProperty("spring.mail.protocol", "smtp").toLowerCase(Locale.ROOT);
        boolean ssl = protocol.contains("smtps")
                || environment.getProperty("spring.mail.properties.mail.smtp.ssl.enable",
                Boolean.class, false);
        boolean starttls = environment.getProperty(
                "spring.mail.properties.mail.smtp.starttls.enable",
                Boolean.class,
                false
        );
        MailSecurity security = ssl
                ? MailSecurity.SSL
                : starttls ? MailSecurity.STARTTLS : MailSecurity.NONE;
        int port = parseInt(
                environment.getProperty("spring.mail.port"),
                security == MailSecurity.SSL ? 465 : security == MailSecurity.STARTTLS ? 587 : 25
        );
        update(new UpdateRequest(
                true,
                legacyHost,
                port,
                environment.getProperty("spring.mail.username", ""),
                environment.getProperty("spring.mail.password", ""),
                false,
                security,
                environment.getProperty("spring.mail.properties.mail.smtp.auth",
                        Boolean.class, true),
                millisecondsToSeconds(environment.getProperty(
                        "spring.mail.properties.mail.smtp.connectiontimeout"), 10),
                millisecondsToSeconds(environment.getProperty(
                        "spring.mail.properties.mail.smtp.timeout"), 15),
                millisecondsToSeconds(environment.getProperty(
                        "spring.mail.properties.mail.smtp.writetimeout"), 15),
                current.smtp().fromAddress(),
                current.smtp().fromName(),
                current.pop3().enabled(),
                current.pop3().host(),
                current.pop3().port(),
                current.pop3().username(),
                null,
                false,
                current.pop3().security(),
                current.pop3().replyAddress(),
                current.pop3().pollIntervalSeconds(),
                current.pop3().connectionTimeoutSeconds(),
                current.pop3().maxMessagesPerPoll(),
                current.systemSender(),
                current.userSender()
        ), null);
        log.info("Migrated legacy spring.mail settings into backend-managed SMTP configuration");
        return true;
    }

    private MailRuntimeSettings load() {
        try {
            Map<String, String> values = databaseConfigService.getRawValues(KEYS);
            return new MailRuntimeSettings(
                    new Smtp(
                            parseBoolean(values.get(SMTP_ENABLED), DEFAULTS.smtp().enabled()),
                            text(values.get(SMTP_HOST), DEFAULTS.smtp().host()),
                            parseInt(values.get(SMTP_PORT), DEFAULTS.smtp().port()),
                            text(values.get(SMTP_USERNAME), DEFAULTS.smtp().username()),
                            text(values.get(SMTP_PASSWORD), DEFAULTS.smtp().password()),
                            parseSecurity(values.get(SMTP_SECURITY), DEFAULTS.smtp().security()),
                            parseBoolean(values.get(SMTP_AUTH), DEFAULTS.smtp().auth()),
                            parseInt(values.get(SMTP_CONNECTION_TIMEOUT),
                                    DEFAULTS.smtp().connectionTimeoutSeconds()),
                            parseInt(values.get(SMTP_READ_TIMEOUT),
                                    DEFAULTS.smtp().readTimeoutSeconds()),
                            parseInt(values.get(SMTP_WRITE_TIMEOUT),
                                    DEFAULTS.smtp().writeTimeoutSeconds()),
                            text(values.get(SMTP_FROM_ADDRESS), DEFAULTS.smtp().fromAddress()),
                            text(values.get(SMTP_FROM_NAME), DEFAULTS.smtp().fromName())
                    ),
                    new Pop3(
                            parseBoolean(values.get(POP3_ENABLED), DEFAULTS.pop3().enabled()),
                            text(values.get(POP3_HOST), DEFAULTS.pop3().host()),
                            parseInt(values.get(POP3_PORT), DEFAULTS.pop3().port()),
                            text(values.get(POP3_USERNAME), DEFAULTS.pop3().username()),
                            text(values.get(POP3_PASSWORD), DEFAULTS.pop3().password()),
                            parseSecurity(values.get(POP3_SECURITY), DEFAULTS.pop3().security()),
                            text(values.get(POP3_REPLY_ADDRESS), DEFAULTS.pop3().replyAddress()),
                            parseInt(values.get(POP3_POLL_INTERVAL),
                                    DEFAULTS.pop3().pollIntervalSeconds()),
                            parseInt(values.get(POP3_CONNECTION_TIMEOUT),
                                    DEFAULTS.pop3().connectionTimeoutSeconds()),
                            parseInt(values.get(POP3_MAX_MESSAGES),
                                    DEFAULTS.pop3().maxMessagesPerPoll())
                    ),
                    new SenderIdentity(
                            text(values.get(SYSTEM_FROM_ADDRESS), DEFAULTS.systemSender().address()),
                            text(values.get(SYSTEM_FROM_NAME), DEFAULTS.systemSender().name())
                    ),
                    new SenderIdentity(
                            text(values.get(USER_FROM_ADDRESS), DEFAULTS.userSender().address()),
                            text(values.get(USER_FROM_NAME), DEFAULTS.userSender().name())
                    )
            );
        } catch (Exception exception) {
            log.warn("Unable to load backend mail settings: {}", exception.getMessage());
            return cached == null ? DEFAULTS : cached;
        }
    }

    private MailRuntimeSettings normalize(
            UpdateRequest request,
            MailRuntimeSettings current,
            String smtpPassword,
            String pop3Password
    ) {
        return new MailRuntimeSettings(
                new Smtp(
                        request.smtpEnabled(),
                        trim(request.smtpHost()),
                        request.smtpPort(),
                        trim(request.smtpUsername()),
                        smtpPassword,
                        request.smtpSecurity() == null
                                ? current.smtp().security()
                                : request.smtpSecurity(),
                        request.smtpAuth(),
                        request.smtpConnectionTimeoutSeconds(),
                        request.smtpReadTimeoutSeconds(),
                        request.smtpWriteTimeoutSeconds(),
                        trim(request.smtpFromAddress()),
                        trim(request.smtpFromName())
                ),
                new Pop3(
                        request.pop3Enabled(),
                        trim(request.pop3Host()),
                        request.pop3Port(),
                        trim(request.pop3Username()),
                        pop3Password,
                        request.pop3Security() == null
                                ? current.pop3().security()
                                : request.pop3Security(),
                        trim(request.pop3ReplyAddress()),
                        request.pop3PollIntervalSeconds(),
                        request.pop3ConnectionTimeoutSeconds(),
                        request.pop3MaxMessagesPerPoll()
                ),
                normalizeSender(request.systemSender(), current.systemSender()),
                normalizeSender(request.userSender(), current.userSender())
        );
    }

    private void validate(MailRuntimeSettings settings) throws BusinessException {
        Smtp smtp = settings.smtp();
        if (smtp.port() < 1 || smtp.port() > 65_535) {
            throw badRequest("SMTP port must be between 1 and 65535");
        }
        validateTimeouts(
                smtp.connectionTimeoutSeconds(),
                smtp.readTimeoutSeconds(),
                smtp.writeTimeoutSeconds(),
                "SMTP"
        );
        validateAddressIfPresent(smtp.fromAddress(), "SMTP fallback sender address");
        validateSender(settings.systemSender(), "system sender");
        validateSender(settings.userSender(), "user message sender");
        if (smtp.enabled()) {
            if (smtp.host().isBlank()) {
                throw badRequest("SMTP host is required when SMTP is enabled");
            }
            if (smtp.auth() && smtp.username().isBlank()) {
                throw badRequest("SMTP username is required when authentication is enabled");
            }
            if (smtp.auth() && smtp.password().isBlank()) {
                throw badRequest("SMTP password is required when authentication is enabled");
            }
            if (!validAddress(systemSenderAddress(settings))
                    || !validAddress(userSenderAddress(settings))) {
                throw badRequest("A valid sender address is required for each mail category");
            }
        }

        Pop3 pop3 = settings.pop3();
        if (pop3.port() < 1 || pop3.port() > 65_535) {
            throw badRequest("POP3 port must be between 1 and 65535");
        }
        if (pop3.connectionTimeoutSeconds() < 1 || pop3.connectionTimeoutSeconds() > 120) {
            throw badRequest("POP3 connection timeout must be between 1 and 120 seconds");
        }
        if (pop3.pollIntervalSeconds() < 15 || pop3.pollIntervalSeconds() > 86_400) {
            throw badRequest("POP3 poll interval must be between 15 and 86400 seconds");
        }
        if (pop3.maxMessagesPerPoll() < 1 || pop3.maxMessagesPerPoll() > 1_000) {
            throw badRequest("POP3 messages per poll must be between 1 and 1000");
        }
        validateAddressIfPresent(pop3.replyAddress(), "POP3 reply address");
        if (pop3.enabled()) {
            if (pop3.host().isBlank() || pop3.username().isBlank()) {
                throw badRequest("POP3 host and username are required when POP3 is enabled");
            }
            if (pop3.password().isBlank()) {
                throw badRequest("POP3 password is required when POP3 is enabled");
            }
            if (!validAddress(replyAddress(settings))) {
                throw badRequest("POP3 username or reply address must be a valid email address");
            }
        }
    }

    private void validateTimeout(int value, String label) throws BusinessException {
        if (value < 1 || value > 120) {
            throw badRequest(label + " must be between 1 and 120 seconds");
        }
    }

    private void validateTimeouts(int connection, int read, int write, String prefix)
            throws BusinessException {
        validateTimeout(connection, prefix + " connection timeout");
        validateTimeout(read, prefix + " read timeout");
        validateTimeout(write, prefix + " write timeout");
    }

    private void validateSender(SenderIdentity sender, String label) throws BusinessException {
        if (sender == null || sender.name().isBlank() || sender.name().length() > 255) {
            throw badRequest("A display name of at most 255 characters is required for the " + label);
        }
        validateAddressIfPresent(sender.address(), label + " address");
    }

    private void validateAddressIfPresent(String value, String label) throws BusinessException {
        if (value != null && !value.isBlank() && !validAddress(value)) {
            throw badRequest(label + " is not a valid email address");
        }
    }

    private SenderIdentity normalizeSender(SenderIdentity requested, SenderIdentity fallback) {
        if (requested == null) {
            return fallback;
        }
        return new SenderIdentity(trim(requested.address()), trim(requested.name()));
    }

    private String resolveSecret(String current, String requested, boolean clear) {
        if (clear) {
            return "";
        }
        return requested == null ? current : requested;
    }

    private MailSecurity parseSecurity(String value, MailSecurity fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return MailSecurity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value);
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int millisecondsToSeconds(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long millis = Long.parseLong(value.trim());
            return (int) Math.max(1, Math.min(120, Math.ceil(millis / 1000.0)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String text(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    public static boolean validAddress(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            InternetAddress[] addresses = InternetAddress.parse(value, true);
            if (addresses.length != 1) {
                return false;
            }
            addresses[0].validate();
            return addresses[0].getAddress() != null
                    && addresses[0].getAddress().contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }

    public record UpdateRequest(
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
            SenderIdentity systemSender,
            SenderIdentity userSender
    ) {
    }
}
