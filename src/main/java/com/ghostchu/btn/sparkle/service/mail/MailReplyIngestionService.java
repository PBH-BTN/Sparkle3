package com.ghostchu.btn.sparkle.service.mail;

import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.entity.MailInboundMessage;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.MailInboundMessageMapper;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.MailSecurity;
import com.ghostchu.btn.sparkle.service.mail.MailRuntimeSettings.Pop3;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class MailReplyIngestionService {
    private static final String LOCK_KEY = "sparkle:mail:pop3:poll";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final TaskStatusService tasks;
    private final MailSettingsService settingsService;
    private final InboundMailParser parser;
    private final MailInboundMessageMapper inboundMapper;
    private final UserMessageMapper messageMapper;
    private final UserMessageService messageService;
    private final IUserService userService;
    @Qualifier("stringStringRedisTemplate")
    private final RedisTemplate<String, String> redisTemplate;
    private final AtomicReference<MailIngestionStatus> status =
            new AtomicReference<>(MailIngestionStatus.initial());
    private volatile OffsetDateTime nextAutomaticScanAt;

    public MailReplyIngestionService(
            TaskStatusService tasks,
            MailSettingsService settingsService,
            InboundMailParser parser,
            MailInboundMessageMapper inboundMapper,
            UserMessageMapper messageMapper,
            UserMessageService messageService,
            IUserService userService,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.tasks = tasks;
        tasks.ensure(TaskDefinition.event("mail-reply-scan", TaskGroup.SYNC,
                "admin.tasks.items.mail-reply-scan.name", "admin.tasks.items.mail-reply-scan.description"));
        this.settingsService = settingsService;
        this.parser = parser;
        this.inboundMapper = inboundMapper;
        this.messageMapper = messageMapper;
        this.messageService = messageService;
        this.userService = userService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    @MonitoredScheduledTask(id = "mail-reply-poll", group = TaskGroup.SYNC,
            nameKey = "admin.tasks.items.mail-reply-poll.name",
            descriptionKey = "admin.tasks.items.mail-reply-poll.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = 15000, initialDelayMillis = 15000)
    public void scheduledScan() {
        MailRuntimeSettings settings = settingsService.get();
        if (!settingsService.pop3Configured(settings)) {
            MonitoredTaskOutcome.skipped("Mailbox polling is not configured");
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime next = nextAutomaticScanAt;
        if (next != null && now.isBefore(next)) {
            MonitoredTaskOutcome.skipped("Mailbox scan is not due");
            return;
        }
        try {
            scanNow(false);
        } catch (BusinessException exception) {
            MonitoredTaskOutcome.failure("Mailbox scan did not complete; see server logs");
            log.debug("POP3 scheduled scan deferred: {}", exception.getMessage());
        }
    }

    @EventListener(MailSettingsChangedEvent.class)
    public void settingsChanged() {
        nextAutomaticScanAt = OffsetDateTime.now();
    }

    public MailIngestionStatus status() {
        MailIngestionStatus current = status.get();
        return new MailIngestionStatus(
                current.running(),
                current.lastScanAt(),
                current.lastSuccessAt(),
                nextAutomaticScanAt,
                current.lastImported(),
                current.lastSkipped(),
                current.lastFailed(),
                safeTotalImported(),
                current.lastError()
        );
    }

    public MailIngestionStatus scanNow(boolean manual) throws BusinessException {
        MailRuntimeSettings settings = settingsService.get();
        if (!settingsService.pop3Configured(settings)) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "POP3 is disabled or not fully configured"
            );
        }
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockToken, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            if (manual) {
                throw new BusinessException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Another mail polling task is already running"
                );
            }
            return status();
        }
        var execution = tasks.begin("mail-reply-scan", manual ? "MANUAL" : "SCHEDULED");
        MailIngestionStatus previous = status.get();
        OffsetDateTime startedAt = OffsetDateTime.now();
        status.set(new MailIngestionStatus(
                true, startedAt, previous.lastSuccessAt(), nextAutomaticScanAt,
                0, 0, 0, previous.totalImported(), null
        ));
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        String lastError = null;
        try {
            PollResult result = poll(settings);
            imported = result.imported();
            skipped = result.skipped();
            failed = result.failed();
            lastError = result.lastError();
            nextAutomaticScanAt = OffsetDateTime.now()
                    .plusSeconds(settings.pop3().pollIntervalSeconds());
            log.debug("POP3 mail scan finished: imported={}, skipped={}, failed={}",
                    imported, skipped, failed);
            status.set(new MailIngestionStatus(
                    false,
                    startedAt,
                    OffsetDateTime.now(),
                    nextAutomaticScanAt,
                    imported,
                    skipped,
                    failed,
                    safeTotalImported(),
                    lastError
            ));
            if (failed > 0) {
                execution.failure(new IllegalStateException("Some email replies could not be imported"));
            } else {
                execution.success();
            }
            return status.get();
        } catch (Exception exception) {
            execution.failure(new IllegalStateException("Unable to poll mailbox; see server logs"));
            nextAutomaticScanAt = OffsetDateTime.now().plusSeconds(15);
            String error = safeError(exception);
            log.warn("Unable to poll POP3 mailbox: {}", error);
            status.set(new MailIngestionStatus(
                    false,
                    startedAt,
                    previous.lastSuccessAt(),
                    nextAutomaticScanAt,
                    imported,
                    skipped,
                    failed,
                    safeTotalImported(),
                    error
            ));
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Unable to poll POP3 mailbox: " + error
            );
        } finally {
            releaseLock(lockToken);
        }
    }

    public void testConnection() throws BusinessException {
        MailRuntimeSettings settings = settingsService.get();
        if (!settingsService.pop3Configured(settings)) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "POP3 is disabled or not fully configured"
            );
        }
        try {
            Store store = openStore(settings.pop3());
            try {
                Folder folder = store.getFolder("INBOX");
                folder.open(Folder.READ_ONLY);
                folder.getMessageCount();
                folder.close(false);
            } finally {
                store.close();
            }
        } catch (Exception exception) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "POP3 connection failed: " + safeError(exception)
            );
        }
    }

    private PollResult poll(MailRuntimeSettings settings) throws Exception {
        Pop3 pop3 = settings.pop3();
        String mailbox = mailboxKey(pop3);
        Store store = openStore(pop3);
        Folder folder = null;
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        String lastError = null;
        try {
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            Set<String> processedUids = new HashSet<>(safeSet(inboundMapper.selectUids(mailbox)));
            Message[] messages = folder.getMessages();
            int examined = 0;
            for (int index = messages.length - 1;
                 index >= 0 && examined < pop3.maxMessagesPerPoll();
                 index--) {
                Message message = messages[index];
                if (!(message instanceof MimeMessage mimeMessage)) {
                    continue;
                }
                String uid = Pop3MessageUid.of(folder, mimeMessage);
                if (processedUids.contains(uid)) {
                    continue;
                }
                examined++;
                InboundMailParser.ParsedMessage parsed = parser.parse(mimeMessage);
                ProcessResult result = process(mailbox, uid, parsed);
                processedUids.add(uid);
                if (result.imported()) {
                    imported++;
                } else if (result.failed()) {
                    failed++;
                    lastError = result.error();
                } else {
                    skipped++;
                }
            }
            return new PollResult(imported, skipped, failed, lastError);
        } finally {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
            store.close();
        }
    }

    private ProcessResult process(
            String mailbox,
            String uid,
            InboundMailParser.ParsedMessage parsed
    ) {
        if (parsed == null) {
            return record(mailbox, uid, null, null, null, "IGNORED",
                    "Message could not be parsed", false);
        }
        if (parsed.automatic()) {
            return record(mailbox, uid, parsed, null, null, "IGNORED",
                    "Automatic or bulk replies are ignored", false);
        }
        if (parsed.replyToken() == null) {
            return record(mailbox, uid, parsed, null, null, "IGNORED",
                    "No Sparkle reply token was found", false);
        }
        UserMessage source = messageMapper.selectOne(
                new QueryWrapper<UserMessage>()
                        .eq("reply_token", parsed.replyToken())
                        .last("LIMIT 1")
        );
        if (source == null) {
            return record(mailbox, uid, parsed, parsed.replyToken(), null, "IGNORED",
                    "Unknown or expired reply token", false);
        }
        if (source.getSenderUserId() == null
                || !"DIRECT_MESSAGE".equalsIgnoreCase(source.getCategory())) {
            return record(mailbox, uid, parsed, parsed.replyToken(), source.getId(), "IGNORED",
                    "The source message does not accept email replies", false);
        }
        if (parsed.content() == null || parsed.content().isBlank()) {
            return record(mailbox, uid, parsed, parsed.replyToken(), source.getId(), "IGNORED",
                    "The reply body is empty", false);
        }
        User replyingUser = userService.getById(source.getRecipientUserId());
        if (replyingUser == null
                || !sameAddress(replyingUser.getEmail(), parsed.fromAddress())) {
            return record(mailbox, uid, parsed, parsed.replyToken(), source.getId(), "REJECTED",
                    "The sender address does not match the original recipient", false);
        }
        try {
            long resultMessageId = messageService.replyFromEmail(
                    parsed.replyToken(),
                    parsed.fromAddress(),
                    parsed.subject(),
                    parsed.content()
            );
            return record(
                    mailbox,
                    uid,
                    parsed,
                    parsed.replyToken(),
                    source.getId(),
                    "IMPORTED",
                    null,
                    true,
                    resultMessageId
            );
        } catch (Exception exception) {
            return record(mailbox, uid, parsed, parsed.replyToken(), source.getId(), "FAILED",
                    safeError(exception), true);
        }
    }

    private ProcessResult record(
            String mailbox,
            String uid,
            InboundMailParser.ParsedMessage parsed,
            UUID replyToken,
            Long sourceMessageId,
            String state,
            String error,
            boolean failed
    ) {
        return record(mailbox, uid, parsed, replyToken, sourceMessageId, state, error, failed, null);
    }

    private ProcessResult record(
            String mailbox,
            String uid,
            InboundMailParser.ParsedMessage parsed,
            UUID replyToken,
            Long sourceMessageId,
            String state,
            String error,
            boolean failed,
            Long resultMessageId
    ) {
        MailInboundMessage row = new MailInboundMessage()
                .setMailbox(mailbox)
                .setUid(uid)
                .setMessageId(parsed == null ? null : parsed.messageId())
                .setFromAddress(parsed == null ? null : parsed.fromAddress())
                .setReplyToken(replyToken)
                .setSourceMessageId(sourceMessageId)
                .setResultMessageId(resultMessageId)
                .setStatus(state)
                .setError(truncate(error, 2_000))
                .setReceivedAt(parsed == null ? null : parsed.receivedAt())
                .setProcessedAt(OffsetDateTime.now());
        try {
            inboundMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            return new ProcessResult(false, false, null);
        } catch (Exception exception) {
            return new ProcessResult(false, true, safeError(exception));
        }
        return new ProcessResult("IMPORTED".equals(state), failed, error);
    }

    private Store openStore(Pop3 pop3) throws Exception {
        boolean ssl = pop3.security() == MailSecurity.SSL;
        String prefix = ssl ? "mail.pop3s." : "mail.pop3.";
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty(prefix + "connectiontimeout",
                String.valueOf(pop3.connectionTimeoutSeconds() * 1_000L));
        properties.setProperty(prefix + "timeout",
                String.valueOf(pop3.connectionTimeoutSeconds() * 1_000L));
        if (ssl) {
            properties.setProperty("mail.pop3s.ssl.enable", "true");
            properties.setProperty("mail.pop3s.ssl.checkserveridentity", "true");
        } else if (pop3.security() == MailSecurity.STARTTLS) {
            properties.setProperty("mail.pop3.starttls.enable", "true");
            properties.setProperty("mail.pop3.starttls.required", "true");
        }
        Session session = Session.getInstance(properties);
        Store store = session.getStore(ssl ? "pop3s" : "pop3");
        store.connect(pop3.host(), pop3.port(), pop3.username(), pop3.password());
        return store;
    }

    private String mailboxKey(Pop3 pop3) {
        String value = (pop3.host() + ":" + pop3.port() + "/" + pop3.username())
                .toLowerCase(Locale.ROOT);
        if (value.length() <= 320) {
            return value;
        }
        return "sha256:" + sha256(value);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean sameAddress(String left, String right) {
        return left != null
                && right != null
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private Set<String> safeSet(Set<String> values) {
        return values == null ? Set.of() : values;
    }

    private long safeTotalImported() {
        try {
            return inboundMapper.countImported();
        } catch (Exception exception) {
            MailIngestionStatus current = status.get();
            return current == null ? 0 : current.totalImported();
        }
    }

    private void releaseLock(String token) {
        try {
            String current = redisTemplate.opsForValue().get(LOCK_KEY);
            if (token.equals(current)) {
                redisTemplate.delete(LOCK_KEY);
            }
        } catch (Exception exception) {
            log.warn("Unable to release POP3 polling lock: {}", exception.getMessage());
        }
    }

    private String safeError(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = exception.getClass().getSimpleName();
        }
        return truncate(value, 1_000);
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    private record PollResult(int imported, int skipped, int failed, String lastError) {
    }

    private record ProcessResult(boolean imported, boolean failed, String error) {
    }
}
