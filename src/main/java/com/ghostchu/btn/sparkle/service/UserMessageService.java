package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.InboxMessageView;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.InboxPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.SendMessageRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.SendMessageResult;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.StaffMessageRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.StaffMessageResult;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserMessageService {
    public static final int DAILY_LIMIT = 15;

    private final JdbcTemplate jdbcTemplate;
    private final IUserService userService;
    private final UserPublicProfileService publicProfileService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final MessageMailService messageMailService;

    @Transactional
    public SendMessageResult sendFromUser(
            long senderUserId,
            long recipientUserId,
            SendMessageRequest request,
            String senderIp,
            String senderUserAgent
    ) throws BusinessException {
        User sender = requireUser(senderUserId);
        User recipient = requireUser(recipientUserId);
        if (!publicProfileService.canSendMessage(sender, recipient)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "This user does not accept messages from other users"
            );
        }
        ValidatedMessage message = validate(request == null ? null : request.subject(),
                request == null ? null : request.content());
        int used = reserveDispatch(senderUserId);
        UUID dispatchId = UUID.randomUUID();
        NotificationPreference preference = notificationPreferenceService.preference(
                recipientUserId,
                NotificationCategory.DIRECT_MESSAGE
        );
        boolean emailRequested = preference.emailEnabled();
        boolean initiallyRead = !preference.inboxEnabled();
        insertMessage(
                dispatchId,
                false,
                sender.getId(),
                "USER",
                senderDisplayName(sender),
                recipient.getId(),
                NotificationCategory.DIRECT_MESSAGE,
                message.subject(),
                message.content(),
                true,
                initiallyRead,
                emailRequested,
                false,
                senderIp,
                senderUserAgent
        );
        scheduleEmail(dispatchId);
        return new SendMessageResult(true, DAILY_LIMIT - used - 1);
    }

    @Transactional
    public StaffMessageResult sendAsStaff(
            long actorUserId,
            StaffMessageRequest request,
            String senderIp,
            String senderUserAgent
    ) throws BusinessException {
        User actor = requireUser(actorUserId);
        if (!AdminPolicy.hasModeratorAccess(actor)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "Administrator or moderator access required");
        }
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Message is required");
        }
        ValidatedMessage message = validate(request.subject(), request.content());
        NotificationCategory category = NotificationCategory.parse(request.category())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported notification category"
                ));
        List<Long> recipients = request.recipientId() == null
                ? activeRecipientIds()
                : List.of(requireUser(request.recipientId()).getId());
        if (request.recipientId() != null && request.recipientId().equals(actorUserId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "You cannot send a staff message to yourself");
        }
        int used = reserveDispatch(actorUserId);
        UUID dispatchId = UUID.randomUUID();
        boolean system = request.systemIdentity();
        String senderKind = system ? "SYSTEM" : "USER";
        String senderName = system ? "Sparkle BTN" : actor.getNickname();
        boolean broadcast = request.recipientId() == null;
        List<Object[]> rows = new ArrayList<>();
        for (Long recipientId : recipients) {
            NotificationPreference preference = notificationPreferenceService.preference(
                    recipientId,
                    category
            );
            boolean inboxVisible = category == NotificationCategory.DIRECT_MESSAGE
                    || preference.inboxEnabled();
            boolean emailRequested = request.forceEmail() || preference.emailEnabled();
            if (!inboxVisible && !emailRequested) {
                continue;
            }
            rows.add(messageRow(
                    dispatchId,
                    broadcast,
                    actor.getId(),
                    senderKind,
                    senderName,
                    recipientId,
                    category,
                    message.subject(),
                    message.content(),
                    inboxVisible,
                    category == NotificationCategory.DIRECT_MESSAGE
                            && !preference.inboxEnabled(),
                    emailRequested,
                    request.forceEmail(),
                    senderIp,
                    senderUserAgent
            ));
        }
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "No recipients have enabled a delivery channel for this category");
        }
        jdbcTemplate.batchUpdate(insertSql(), rows);
        scheduleEmail(dispatchId);
        return new StaffMessageResult(
                rows.size(),
                dispatchId,
                DAILY_LIMIT - used - 1
        );
    }

    @Transactional
    public UUID publishSystem(
            NotificationCategory category,
            String subject,
            String content,
            boolean sendEmail
    ) throws BusinessException {
        ValidatedMessage message = validate(subject, content);
        UUID dispatchId = UUID.randomUUID();
        List<Object[]> rows = new ArrayList<>();
        for (Long recipientId : activeRecipientIds()) {
            NotificationPreference preference = notificationPreferenceService.preference(
                    recipientId,
                    category
            );
            boolean inboxVisible = category == NotificationCategory.DIRECT_MESSAGE
                    || preference.inboxEnabled();
            boolean emailRequested = sendEmail && preference.emailEnabled();
            if (!inboxVisible && !emailRequested) {
                continue;
            }
            rows.add(messageRow(
                    dispatchId,
                    true,
                    null,
                    "SYSTEM",
                    "Sparkle BTN",
                    recipientId,
                    category,
                    message.subject(),
                    message.content(),
                    inboxVisible,
                    category == NotificationCategory.DIRECT_MESSAGE
                            && !preference.inboxEnabled(),
                    emailRequested,
                    false,
                    null,
                    null
            ));
        }
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(insertSql(), rows);
            if (sendEmail) {
                scheduleEmail(dispatchId);
            }
        }
        return dispatchId;
    }

    @Transactional
    public boolean publishSystemToUser(
            long recipientUserId,
            NotificationCategory category,
            String subject,
            String content,
            boolean sendEmail
    ) throws BusinessException {
        ValidatedMessage message = validate(subject, content);
        NotificationPreference preference = notificationPreferenceService.preference(
                recipientUserId,
                category
        );
        boolean inboxVisible = category == NotificationCategory.DIRECT_MESSAGE
                || preference.inboxEnabled();
        boolean emailRequested = sendEmail && preference.emailEnabled();
        if (!inboxVisible && !emailRequested) {
            return false;
        }
        UUID dispatchId = UUID.randomUUID();
        insertMessage(
                dispatchId,
                false,
                null,
                "SYSTEM",
                "Sparkle BTN",
                recipientUserId,
                category,
                message.subject(),
                message.content(),
                inboxVisible,
                category == NotificationCategory.DIRECT_MESSAGE
                        && !preference.inboxEnabled(),
                emailRequested,
                false,
                null,
                null
        );
        if (emailRequested) {
            scheduleEmail(dispatchId);
        }
        return true;
    }

    public InboxPage inbox(long userId, long page, long size, boolean unreadOnly) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        String unreadClause = unreadOnly ? " AND read_at IS NULL" : "";
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_messages
                WHERE recipient_user_id = ?
                  AND deleted_at IS NULL
                  AND inbox_visible = TRUE%s
                """.formatted(unreadClause), Long.class, userId);
        long totalValue = total == null ? 0 : total;
        List<InboxMessageView> records = jdbcTemplate.query("""
                SELECT id, sender_display_name, sender_kind, category, subject, content,
                       created_at, read_at, broadcast, email_requested, email_forced,
                       email_status
                FROM user_messages
                WHERE recipient_user_id = ?
                  AND deleted_at IS NULL
                  AND inbox_visible = TRUE%s
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(unreadClause), this::mapMessage,
                userId, safeSize, (safePage - 1) * safeSize
        );
        long pages = totalValue == 0 ? 0 : (totalValue + safeSize - 1) / safeSize;
        return new InboxPage(records, safePage, safeSize, totalValue, pages);
    }

    public int unreadCount(long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_messages
                WHERE recipient_user_id = ?
                  AND deleted_at IS NULL
                  AND inbox_visible = TRUE
                  AND read_at IS NULL
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    public void markRead(long userId, long messageId) {
        jdbcTemplate.update("""
                UPDATE user_messages
                SET read_at = COALESCE(read_at, now())
                WHERE id = ? AND recipient_user_id = ? AND deleted_at IS NULL
                """, messageId, userId);
    }

    public void markAllRead(long userId) {
        jdbcTemplate.update("""
                UPDATE user_messages
                SET read_at = COALESCE(read_at, now())
                WHERE recipient_user_id = ?
                  AND inbox_visible = TRUE
                  AND deleted_at IS NULL
                  AND read_at IS NULL
                """, userId);
    }

    public void delete(long userId, long messageId) {
        jdbcTemplate.update("""
                UPDATE user_messages
                SET deleted_at = now()
                WHERE id = ? AND recipient_user_id = ? AND deleted_at IS NULL
                """, messageId, userId);
    }

    private int reserveDispatch(long senderUserId) throws BusinessException {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                resultSet -> {
                },
                "sparkle:user-message:" + senderUserId
        );
        Integer used = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT dispatch_id)
                FROM user_messages
                WHERE sender_user_id = ?
                  AND created_at >= (
                      date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')
                      AT TIME ZONE 'Asia/Shanghai'
                  )
                """, Integer.class, senderUserId);
        int usedValue = used == null ? 0 : used;
        if (usedValue >= DAILY_LIMIT) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily direct message limit reached");
        }
        return usedValue;
    }

    private void insertMessage(
            UUID dispatchId,
            boolean broadcast,
            Long senderUserId,
            String senderKind,
            String senderDisplayName,
            long recipientUserId,
            NotificationCategory category,
            String subject,
            String content,
            boolean inboxVisible,
            boolean initiallyRead,
            boolean emailRequested,
            boolean emailForced,
            String senderIp,
            String senderUserAgent
    ) {
        jdbcTemplate.update(insertSql(), messageRow(
                dispatchId,
                broadcast,
                senderUserId,
                senderKind,
                senderDisplayName,
                recipientUserId,
                category,
                subject,
                content,
                inboxVisible,
                initiallyRead,
                emailRequested,
                emailForced,
                senderIp,
                senderUserAgent
        ));
    }

    private Object[] messageRow(
            UUID dispatchId,
            boolean broadcast,
            Long senderUserId,
            String senderKind,
            String senderDisplayName,
            long recipientUserId,
            NotificationCategory category,
            String subject,
            String content,
            boolean inboxVisible,
            boolean initiallyRead,
            boolean emailRequested,
            boolean emailForced,
            String senderIp,
            String senderUserAgent
    ) {
        return new Object[]{
                dispatchId,
                broadcast,
                senderUserId,
                senderKind,
                senderDisplayName,
                recipientUserId,
                category.name(),
                subject,
                content,
                inboxVisible,
                emailRequested,
                emailForced,
                emailRequested ? "PENDING" : "NONE",
                normalizeIp(senderIp),
                truncate(normalizeHeader(senderUserAgent), 500),
                initiallyRead ? OffsetDateTime.now() : null
        };
    }

    private String insertSql() {
        return """
                INSERT INTO user_messages (
                    dispatch_id, broadcast, sender_user_id, sender_kind,
                    sender_display_name, recipient_user_id, category,
                    subject, content, inbox_visible, email_requested,
                    email_forced, email_status, sender_ip, sender_user_agent, read_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?)
                """;
    }

    private InboxMessageView mapMessage(java.sql.ResultSet resultSet, int rowNum)
            throws java.sql.SQLException {
        OffsetDateTime readAt = resultSet.getObject("read_at", OffsetDateTime.class);
        return new InboxMessageView(
                resultSet.getLong("id"),
                resultSet.getString("sender_display_name"),
                resultSet.getString("sender_kind"),
                resultSet.getString("category"),
                resultSet.getString("subject"),
                resultSet.getString("content"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                readAt,
                readAt != null,
                resultSet.getBoolean("broadcast"),
                resultSet.getBoolean("email_requested"),
                resultSet.getBoolean("email_forced"),
                resultSet.getString("email_status")
        );
    }

    private List<Long> activeRecipientIds() {
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM "user"
                WHERE banned_at IS NULL
                  AND LOWER(email) NOT LIKE '%@sparkle.system'
                ORDER BY id
                """, Long.class);
    }

    private ValidatedMessage validate(String subject, String content)
            throws BusinessException {
        String safeSubject = normalizeHeader(subject);
        String safeContent = content == null ? "" : content.replace("\r\n", "\n").trim();
        if (safeSubject == null || safeSubject.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Message subject is required");
        }
        if (safeSubject.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Message subject is too long");
        }
        if (safeContent.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Message content is required");
        }
        if (safeContent.length() > 20_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Message content is too long");
        }
        return new ValidatedMessage(safeSubject, safeContent);
    }

    private String senderDisplayName(User sender) {
        UserPrivacySettings privacy = publicProfileService.privacySettings(sender);
        if (privacy.allowsFullUsername()) {
            return sender.getNickname();
        }
        return publicProfileService.blurNickname(sender.getNickname());
    }

    private User requireUser(long userId) throws BusinessException {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").trim();
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 64 ? normalized : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void scheduleEmail(UUID dispatchId) {
        afterCommit(() -> Thread.startVirtualThread(() -> {
            try {
                messageMailService.deliverDispatch(dispatchId);
            } catch (Exception exception) {
                log.error("Unable to process email notifications for dispatch {}: {}",
                        dispatchId, exception.getMessage(), exception);
            }
        }));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }

    private record ValidatedMessage(String subject, String content) {
    }
}
