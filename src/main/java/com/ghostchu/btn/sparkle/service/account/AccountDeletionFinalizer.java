package com.ghostchu.btn.sparkle.service.account;

import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionFinalizer {
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountDeletionPendingRegistry pendingRegistry;

    @Transactional
    public boolean finalizeDeletion(long userId) {
        List<RequestRow> requests = jdbcTemplate.query("""
                SELECT status, requested_at, cooling_ends_at
                FROM user_account_deletion_request
                WHERE user_id = ?
                FOR UPDATE
                """, (resultSet, rowNum) -> new RequestRow(
                resultSet.getString("status"),
                resultSet.getObject("requested_at", OffsetDateTime.class),
                resultSet.getObject("cooling_ends_at", OffsetDateTime.class)
        ), userId);
        if (requests.isEmpty()) {
            afterCommit(() -> pendingRegistry.clear(userId));
            return false;
        }
        RequestRow request = requests.getFirst();
        OffsetDateTime now = OffsetDateTime.now();
        if (!"PENDING".equals(request.status())
                || request.coolingEndsAt().isAfter(now)) {
            if (!"PENDING".equals(request.status())) {
                afterCommit(() -> pendingRegistry.clear(userId));
            }
            return false;
        }

        List<DeletedUser> users = jdbcTemplate.query("""
                SELECT id, email, nickname
                FROM "user"
                WHERE id = ?
                FOR UPDATE
                """, (resultSet, rowNum) -> new DeletedUser(
                resultSet.getLong("id"),
                resultSet.getString("email"),
                resultSet.getString("nickname")
        ), userId);
        if (users.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE user_account_deletion_request
                    SET status = 'CANCELLED',
                        cancelled_at = ?,
                        cancellation_reason = 'USER_NOT_FOUND',
                        updated_at = ?
                    WHERE user_id = ?
                      AND status = 'PENDING'
                    """, now, now, userId);
            afterCommit(() -> pendingRegistry.clear(userId));
            return false;
        }

        DeletedUser user = users.getFirst();
        String anonymousNickname = userId + "-userdeleted";
        String anonymousEmail = userId + "@userdeleted.pbh-btn.com";
        jdbcTemplate.update("""
                UPDATE userapp
                SET banned_at = COALESCE(banned_at, ?),
                    banned_reason = ?,
                    comment = ?
                WHERE owner = ?
                """, now,
                UserAccountDeletionService.DELETED_APP_REASON,
                UserAccountDeletionService.DELETED_APP_REASON,
                userId
        );
        jdbcTemplate.update("""
                UPDATE user_rel
                SET platform_user_id = '0',
                    platform_user_login = ?,
                    platform_user_email = ?
                WHERE bind_user_id = ?
                  AND platform = 'github'
                """, anonymousNickname, anonymousEmail, userId);
        jdbcTemplate.update("""
                UPDATE user_contact_email_deliveries
                SET sender_nickname = CASE
                        WHEN sender_user_id = ? THEN ?
                        ELSE sender_nickname
                    END,
                    sender_email = CASE
                        WHEN sender_user_id = ? THEN ?
                        ELSE sender_email
                    END,
                    sender_ip = CASE
                        WHEN sender_user_id = ? THEN NULL
                        ELSE sender_ip
                    END,
                    sender_user_agent = CASE
                        WHEN sender_user_id = ? THEN NULL
                        ELSE sender_user_agent
                    END,
                    recipient_nickname = CASE
                        WHEN recipient_user_id = ? THEN ?
                        ELSE recipient_nickname
                    END,
                    recipient_email = CASE
                        WHEN recipient_user_id = ? THEN ?
                        ELSE recipient_email
                    END
                WHERE sender_user_id = ?
                   OR recipient_user_id = ?
                """,
                userId, anonymousNickname,
                userId, anonymousEmail,
                userId,
                userId,
                userId, anonymousNickname,
                userId, anonymousEmail,
                userId, userId
        );
        jdbcTemplate.update("""
                UPDATE "user"
                SET email = ?,
                    nickname = ?,
                    avatar = '/img/anonymous-user.png',
                    privacy_level = ?,
                    role = 'user',
                    banned_at = COALESCE(banned_at, ?),
                    banned_reason = ?
                WHERE id = ?
                """, anonymousEmail,
                anonymousNickname,
                UserPrivacyLevel.HIGH.getLevel(),
                now,
                UserAccountDeletionService.DELETED_APP_REASON,
                userId
        );
        jdbcTemplate.update("""
                UPDATE user_account_deletion_request
                SET status = 'COMPLETED',
                    completed_at = ?,
                    last_error = NULL,
                    updated_at = ?
                WHERE user_id = ?
                  AND status = 'PENDING'
                """, now, now, userId);

        eventPublisher.publishEvent(new AccountDeletionNotificationEvent(
                userId,
                user.email(),
                user.nickname(),
                AccountDeletionNotificationEvent.Kind.COMPLETED,
                request.requestedAt(),
                request.coolingEndsAt(),
                now,
                null
        ));
        afterCommit(() -> pendingRegistry.clear(userId));
        log.info("Completed account deletion and anonymization for user {}", userId);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelAfterFinalizationFailure(long userId, Throwable failure) {
        OffsetDateTime now = OffsetDateTime.now();
        List<RequestRow> requests = jdbcTemplate.query("""
                SELECT status, requested_at, cooling_ends_at
                FROM user_account_deletion_request
                WHERE user_id = ?
                FOR UPDATE
                """, (resultSet, rowNum) -> new RequestRow(
                resultSet.getString("status"),
                resultSet.getObject("requested_at", OffsetDateTime.class),
                resultSet.getObject("cooling_ends_at", OffsetDateTime.class)
        ), userId);
        if (requests.isEmpty()) {
            return;
        }
        RequestRow request = requests.getFirst();
        if (!"PENDING".equals(request.status())) {
            return;
        }
        String message = failure == null || failure.getMessage() == null
                ? "Unknown finalization error"
                : failure.getMessage();
        int updated = jdbcTemplate.update("""
                UPDATE user_account_deletion_request
                SET status = 'CANCELLED',
                    cancelled_at = ?,
                    cancellation_reason = 'FINALIZATION_FAILED',
                    last_error = ?,
                    updated_at = ?
                WHERE user_id = ?
                  AND status = 'PENDING'
                """, now, limit(message, 2_000), now, userId);
        if (updated == 0) {
            return;
        }
        List<DeletedUser> users = jdbcTemplate.query("""
                SELECT id, email, nickname
                FROM "user"
                WHERE id = ?
                """, (resultSet, rowNum) -> new DeletedUser(
                resultSet.getLong("id"),
                resultSet.getString("email"),
                resultSet.getString("nickname")
        ), userId);
        if (users.isEmpty()) {
            return;
        }
        DeletedUser user = users.getFirst();
        eventPublisher.publishEvent(new AccountDeletionNotificationEvent(
                userId,
                user.email(),
                user.nickname(),
                AccountDeletionNotificationEvent.Kind.CANCELLED,
                request.requestedAt(),
                request.coolingEndsAt(),
                null,
                "系统在处理注销时发生错误，本次申请已取消；账号资料未被匿名化，请稍后重新申请"
        ));
        afterCommit(() -> pendingRegistry.clear(userId));
    }

    private record RequestRow(
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime coolingEndsAt
    ) {
    }

    private record DeletedUser(long id, String email, String nickname) {
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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
}
