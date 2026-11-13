package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.InboxMessageView;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.InboxPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.SendMessageRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.SendMessageResult;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.StaffMessageRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.StaffMessageResult;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserMessageService {
    public static final int DAILY_LIMIT = 15;

    private final UserMessageMapper messageMapper;
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
        List<UserMessage> rows = new ArrayList<>();
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
        rows.forEach(messageMapper::insert);
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
        List<UserMessage> rows = new ArrayList<>();
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
            rows.forEach(messageMapper::insert);
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
        Page<UserMessage> result = messageMapper.selectConversations(
                new Page<>(safePage, safeSize), userId, unreadOnly);
        long totalValue = result.getTotal();
        List<InboxMessageView> records = result.getRecords().stream()
                .map(message -> mapMessage(message, userId))
                .toList();
        long pages = totalValue == 0 ? 0 : (totalValue + safeSize - 1) / safeSize;
        return new InboxPage(records, safePage, safeSize, totalValue, pages);
    }

    public InboxPage conversation(long userId, long messageId, long page, long size)
            throws BusinessException {
        UserMessage anchor = messageMapper.selectById(messageId);
        if (anchor == null || !canView(userId, anchor)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Message not found");
        }
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        QueryWrapper<UserMessage> query = new QueryWrapper<>();
        if (isConversation(anchor)) {
            long peerId = anchor.getSenderUserId() == userId
                    ? anchor.getRecipientUserId() : anchor.getSenderUserId();
            query.eq("category", NotificationCategory.DIRECT_MESSAGE.name())
                    .eq("sender_kind", "USER").eq("broadcast", false)
                    .and(q -> q.nested(incoming -> incoming.eq("recipient_user_id", userId)
                                    .eq("sender_user_id", peerId).isNull("deleted_at")
                                    .eq("inbox_visible", true))
                            .or(outgoing -> outgoing.eq("sender_user_id", userId)
                                    .eq("recipient_user_id", peerId)));
        } else {
            query.eq("id", messageId);
        }
        query.orderByDesc("created_at").orderByDesc("id");
        Page<UserMessage> result = messageMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new InboxPage(result.getRecords().stream().map(m -> mapMessage(m, userId)).toList(),
                safePage, safeSize, result.getTotal(), result.getPages());
    }

    private boolean isConversation(UserMessage message) {
        return "USER".equals(message.getSenderKind()) && message.getSenderUserId() != null
                && NotificationCategory.DIRECT_MESSAGE.name().equals(message.getCategory())
                && !Boolean.TRUE.equals(message.getBroadcast());
    }

    private boolean canView(long userId, UserMessage message) {
        return (message.getRecipientUserId() == userId && message.getDeletedAt() == null
                && Boolean.TRUE.equals(message.getInboxVisible()))
                || (isConversation(message) && message.getSenderUserId() == userId);
    }

    public int unreadCount(long userId) {
        return Math.toIntExact(messageMapper.selectCount(
                new QueryWrapper<UserMessage>()
                        .eq("recipient_user_id", userId)
                        .isNull("deleted_at")
                        .eq("inbox_visible", true)
                        .isNull("read_at")
        ));
    }

    public void markRead(long userId, long messageId) {
        messageMapper.update(null,
                new LambdaUpdateWrapper<UserMessage>()
                        .set(UserMessage::getReadAt, OffsetDateTime.now())
                        .eq(UserMessage::getId, messageId)
                        .eq(UserMessage::getRecipientUserId, userId)
                        .isNull(UserMessage::getReadAt)
                        .isNull(UserMessage::getDeletedAt)
        );
    }

    public void markAllRead(long userId) {
        messageMapper.update(null,
                new LambdaUpdateWrapper<UserMessage>()
                        .set(UserMessage::getReadAt, OffsetDateTime.now())
                        .eq(UserMessage::getRecipientUserId, userId)
                        .eq(UserMessage::getInboxVisible, true)
                        .isNull(UserMessage::getDeletedAt)
                        .isNull(UserMessage::getReadAt)
        );
    }

    public void delete(long userId, long messageId) {
        messageMapper.update(null,
                new LambdaUpdateWrapper<UserMessage>()
                        .set(UserMessage::getDeletedAt, OffsetDateTime.now())
                        .eq(UserMessage::getId, messageId)
                        .eq(UserMessage::getRecipientUserId, userId)
                        .isNull(UserMessage::getDeletedAt)
        );
    }

    @Transactional
    public long replyFromEmail(
            UUID replyToken,
            String fromAddress,
            String replySubject,
            String replyContent
    ) throws BusinessException {
        if (replyToken == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Reply token is required");
        }
        UserMessage original = messageMapper.selectOne(
                new QueryWrapper<UserMessage>()
                        .eq("reply_token", replyToken)
                        .last("LIMIT 1")
        );
        if (original == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "The message referenced by this email reply no longer exists");
        }
        if (!NotificationCategory.DIRECT_MESSAGE.name().equals(original.getCategory())
                || original.getSenderUserId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "This message does not accept email replies");
        }
        User sender = requireUser(original.getRecipientUserId());
        User recipient = requireUser(original.getSenderUserId());
        String expectedAddress = normalizeEmail(sender.getEmail());
        if (expectedAddress == null
                || !expectedAddress.equals(normalizeEmail(fromAddress))) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "The replying email address does not match the original recipient");
        }
        if (sender.getId().equals(recipient.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "A message cannot be replied to itself");
        }

        String subject = replySubject == null || replySubject.isBlank()
                ? original.getSubject()
                : replySubject;
        if (!subject.toLowerCase(Locale.ROOT).startsWith("re:")) {
            subject = "Re: " + subject;
        }
        ValidatedMessage validated = validate(subject, replyContent);
        reserveDispatch(sender.getId());
        NotificationPreference preference = notificationPreferenceService.preference(
                recipient.getId(),
                NotificationCategory.DIRECT_MESSAGE
        );
        UUID dispatchId = UUID.randomUUID();
        UserMessage inserted = insertMessage(
                dispatchId,
                false,
                sender.getId(),
                "USER",
                senderDisplayName(sender),
                recipient.getId(),
                NotificationCategory.DIRECT_MESSAGE,
                validated.subject(),
                validated.content(),
                true,
                !preference.inboxEnabled(),
                preference.emailEnabled(),
                false,
                null,
                "POP3 email reply"
        );
        if (preference.emailEnabled()) {
            scheduleEmail(dispatchId);
        }
        log.info("Imported email reply for message {} as inbox message {}",
                original.getId(), inserted.getId());
        return inserted.getId();
    }

    private int reserveDispatch(long senderUserId) throws BusinessException {
        messageMapper.lockKey("sparkle:user-message:" + senderUserId);
        int usedValue = messageMapper.countDailyDispatches(senderUserId);
        if (usedValue >= DAILY_LIMIT) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily direct message limit reached");
        }
        return usedValue;
    }

    private UserMessage insertMessage(
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
        UserMessage message = messageRow(
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
        );
        messageMapper.insert(message);
        return message;
    }

    private UserMessage messageRow(
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
        return new UserMessage()
                .setDispatchId(dispatchId)
                .setBroadcast(broadcast)
                .setSenderUserId(senderUserId)
                .setSenderKind(senderKind)
                .setSenderDisplayName(senderDisplayName)
                .setRecipientUserId(recipientUserId)
                .setCategory(category.name())
                .setSubject(subject)
                .setContent(content)
                .setInboxVisible(inboxVisible)
                .setEmailRequested(emailRequested)
                .setEmailForced(emailForced)
                .setEmailStatus(emailRequested ? "PENDING" : "NONE")
                .setReplyToken(senderUserId != null
                        && category == NotificationCategory.DIRECT_MESSAGE
                        ? UUID.randomUUID()
                        : null)
                .setSenderIp(parseIp(senderIp))
                .setSenderUserAgent(truncate(normalizeHeader(senderUserAgent), 500))
                .setReadAt(initiallyRead ? OffsetDateTime.now() : null);
    }

    private InboxMessageView mapMessage(UserMessage message, long userId) {
        boolean outgoing = isConversation(message) && message.getSenderUserId() == userId;
        Long peerId = isConversation(message)
                ? (outgoing ? message.getRecipientUserId() : message.getSenderUserId()) : null;
        User peer = peerId == null ? null : userService.getById(peerId);
        String peerName = peer == null ? message.getSenderDisplayName() : senderDisplayName(peer);
        return new InboxMessageView(
                message.getId(),
                outgoing ? message.getSenderDisplayName() : peerName,
                message.getSenderKind(),
                message.getCategory(),
                message.getSubject(),
                message.getContent(),
                message.getCreatedAt(),
                message.getReadAt(),
                message.getHasUnread() != null ? !message.getHasUnread()
                        : outgoing || message.getReadAt() != null,
                Boolean.TRUE.equals(message.getBroadcast()),
                Boolean.TRUE.equals(message.getEmailRequested()),
                Boolean.TRUE.equals(message.getEmailForced()),
                message.getEmailStatus(),
                outgoing,
                peerId == null ? null : peerId.toString(),
                peerName
        );
    }

    private List<Long> activeRecipientIds() {
        return userService.list(
                new QueryWrapper<User>()
                        .isNull("banned_at")
                        .notLike("email", "@sparkle.system")
                        .orderByAsc("id")
        ).stream().map(User::getId).toList();
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
        if (!sender.isNicknamePubliclyVisible()) {
            return "***";
        }
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

    private InetAddress parseIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
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
