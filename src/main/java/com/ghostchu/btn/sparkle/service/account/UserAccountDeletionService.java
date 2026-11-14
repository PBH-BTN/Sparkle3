package com.ghostchu.btn.sparkle.service.account;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.entity.UserAccountDeletionRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserAccountDeletionRequestMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountDeletionService {
    public static final String REAUTH_SESSION_ATTRIBUTE =
            UserAccountDeletionService.class.getName() + ".REAUTH";
    public static final Duration COOLING_PERIOD = Duration.ofDays(7);
    public static final Duration REAUTHENTICATION_LIFETIME = Duration.ofMinutes(10);
    public static final String DELETED_APP_REASON = "用户已注销";

    private static final String PENDING = "PENDING";
    private static final String CANCELLED = "CANCELLED";
    private static final String COMPLETED = "COMPLETED";
    private static final int FINALIZATION_BATCH_SIZE = 100;

    private final UserAccountDeletionRequestMapper requestMapper;
    private final IUserService userService;
    private final AccountDeletionFinalizer finalizer;
    private final AccountDeletionPendingRegistry pendingRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public ReauthenticationIntent createReauthenticationIntent(long userId)
            throws BusinessException {
        User user = requireUser(userId);
        ensureDeletableUser(user);
        UserRel relation = userService.getUserRelByBindUserId(userId);
        if (relation == null || !"github".equalsIgnoreCase(relation.getPlatform())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "A verified GitHub binding is required before account deletion"
            );
        }
        AccountDeletionStatus status = status(userId);
        if (PENDING.equals(status.state())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Account deletion is already pending");
        }
        if (COMPLETED.equals(status.state())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This account has already been deleted");
        }
        return new ReauthenticationIntent(
                userId,
                OffsetDateTime.now().plus(REAUTHENTICATION_LIFETIME).toEpochSecond()
        );
    }

    public ReauthenticationIntent reauthenticationIntent(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(REAUTH_SESSION_ATTRIBUTE);
        if (!(value instanceof ReauthenticationIntent intent)) {
            return null;
        }
        if (intent.expiresAtEpochSecond() < OffsetDateTime.now().toEpochSecond()) {
            session.removeAttribute(REAUTH_SESSION_ATTRIBUTE);
            return null;
        }
        return intent;
    }

    public boolean githubAccountMatches(long userId, long githubUid) {
        UserRel relation = userService.getUserRelByBindUserId(userId);
        return relation != null
                && "github".equalsIgnoreCase(relation.getPlatform())
                && String.valueOf(githubUid).equals(relation.getPlatformUserId());
    }

    @Transactional
    public AccountDeletionStatus startAfterReauthentication(long userId, long githubUid)
            throws BusinessException {
        User user = requireUser(userId);
        ensureDeletableUser(user);
        if (!githubAccountMatches(userId, githubUid)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "The reauthorized GitHub account does not match the signed-in account"
            );
        }

        RequestRow existing = lockedRequest(userId);
        if (existing != null && PENDING.equals(existing.status())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Account deletion is already pending");
        }
        if (existing != null && COMPLETED.equals(existing.status())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This account has already been deleted");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime coolingEndsAt = now.plus(COOLING_PERIOD);
        if (existing == null) {
            requestMapper.insert(new UserAccountDeletionRequest()
                    .setUserId(userId)
                    .setStatus(PENDING)
                    .setRequestedAt(now)
                    .setCoolingEndsAt(coolingEndsAt)
                    .setUpdatedAt(now));
        } else {
            requestMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update
                            .LambdaUpdateWrapper<UserAccountDeletionRequest>()
                            .set(UserAccountDeletionRequest::getStatus, PENDING)
                            .set(UserAccountDeletionRequest::getRequestedAt, now)
                            .set(UserAccountDeletionRequest::getCoolingEndsAt, coolingEndsAt)
                            .set(UserAccountDeletionRequest::getCancelledAt, null)
                            .set(UserAccountDeletionRequest::getCompletedAt, null)
                            .set(UserAccountDeletionRequest::getCancellationReason, null)
                            .set(UserAccountDeletionRequest::getLastError, null)
                            .set(UserAccountDeletionRequest::getUpdatedAt, now)
                            .eq(UserAccountDeletionRequest::getUserId, userId)
            );
        }
        publish(AccountDeletionNotificationEvent.Kind.STARTED,
                user, now, coolingEndsAt, null, null);
        afterCommit(() -> pendingRegistry.mark(userId, coolingEndsAt));
        return new AccountDeletionStatus(PENDING, now, coolingEndsAt, null, null);
    }

    @Transactional
    public boolean cancelPendingDeletion(long userId, String reasonCode, String detail) {
        return cancelPendingInTransaction(userId, reasonCode, detail);
    }

    public ApplicationActivityDecision recordApplicationActivity(long userId, long userappId) {
        if (!pendingRegistry.isMarked(userId)) {
            return ApplicationActivityDecision.ALLOW;
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        ApplicationActivityDecision decision = transactionTemplate.execute(
                status -> recordApplicationActivityInTransaction(userId, userappId)
        );
        return decision == null ? ApplicationActivityDecision.ALLOW : decision;
    }

    private ApplicationActivityDecision recordApplicationActivityInTransaction(
            long userId,
            long userappId
    ) {
        RequestRow request = lockedRequest(userId);
        if (request == null) {
            afterCommit(() -> pendingRegistry.clear(userId));
            return ApplicationActivityDecision.ALLOW;
        }
        if (COMPLETED.equals(request.status())) {
            afterCommit(() -> pendingRegistry.clear(userId));
            return ApplicationActivityDecision.ACCOUNT_DELETED;
        }
        if (!PENDING.equals(request.status())) {
            afterCommit(() -> pendingRegistry.clear(userId));
            return ApplicationActivityDecision.ALLOW;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!request.coolingEndsAt().isAfter(now)) {
            return ApplicationActivityDecision.ACCOUNT_DELETED;
        }
        boolean cancelled = cancelLockedRequest(
                userId,
                request,
                "APPLICATION_ACTIVITY",
                "检测到用户应用 #" + userappId + " 活动"
        );
        if (!cancelled) {
            return ApplicationActivityDecision.ALLOW;
        }
        log.info("Cancelled pending account deletion for user {} because application {} was active",
                userId, userappId);
        return ApplicationActivityDecision.DELETION_CANCELLED;
    }

    public Long pendingUserIdForGithubAccount(long githubUid) {
        return requestMapper.findPendingUserIdByGithub(String.valueOf(githubUid));
    }

    public AccountDeletionStatus status(long userId) {
        UserAccountDeletionRequest request = requestMapper.selectById(userId);
        if (request == null) {
            return AccountDeletionStatus.none();
        }
        return new AccountDeletionStatus(
                request.getStatus(),
                request.getRequestedAt(),
                request.getCoolingEndsAt(),
                request.getCompletedAt(),
                request.getCancellationReason()
        );
    }

    public void revokeSessions(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            return;
        }
        try {
            var sessions = sessionRepository.findByPrincipalName(principalName);
            sessions.keySet().forEach(sessionRepository::deleteById);
        } catch (Exception exception) {
            log.warn("Unable to revoke all sessions for account deletion principal {}: {}",
                    principalName, exception.getMessage());
        }
    }

    public boolean finalizeIfDue(long userId) {
        return finalizer.finalizeDeletion(userId);
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @MonitoredScheduledTask(
            id = "account-deletion-finalize",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.account-deletion-finalize.name",
            descriptionKey = "admin.tasks.items.account-deletion-finalize.description",
            scheduleType = TaskScheduleType.FIXED_DELAY,
            intervalMillis = 300_000,
            initialDelayMillis = 60_000
    )
    public void processDueDeletions() {
        List<Long> dueUserIds = requestMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query
                        .QueryWrapper<UserAccountDeletionRequest>()
                        .eq("status", PENDING)
                        .le("cooling_ends_at", OffsetDateTime.now())
                        .orderByAsc("cooling_ends_at")
                        .last("LIMIT " + FINALIZATION_BATCH_SIZE)
        ).stream().map(UserAccountDeletionRequest::getUserId).toList();
        for (Long userId : dueUserIds) {
            try {
                finalizer.finalizeDeletion(userId);
            } catch (Exception exception) {
                log.error("Unable to finalize account deletion for user {}: {}",
                        userId, exception.getMessage(), exception);
                try {
                    finalizer.cancelAfterFinalizationFailure(userId, exception);
                } catch (Exception cancellationFailure) {
                    log.error("Unable to cancel failed account deletion for user {}: {}",
                            userId, cancellationFailure.getMessage(), cancellationFailure);
                }
            }
        }
    }

    private boolean cancelPendingInTransaction(
            long userId,
            String reasonCode,
            String detail
    ) {
        RequestRow request = lockedRequest(userId);
        if (request == null || !PENDING.equals(request.status())) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!request.coolingEndsAt().isAfter(now)) {
            return false;
        }
        return cancelLockedRequest(userId, request, reasonCode, detail);
    }

    private boolean cancelLockedRequest(
            long userId,
            RequestRow request,
            String reasonCode,
            String detail
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        User user = requireUser(userId);
        int updated = requestMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update
                        .LambdaUpdateWrapper<UserAccountDeletionRequest>()
                        .set(UserAccountDeletionRequest::getStatus, CANCELLED)
                        .set(UserAccountDeletionRequest::getCancelledAt, now)
                        .set(UserAccountDeletionRequest::getCancellationReason, reasonCode)
                        .set(UserAccountDeletionRequest::getUpdatedAt, now)
                        .eq(UserAccountDeletionRequest::getUserId, userId)
                        .eq(UserAccountDeletionRequest::getStatus, PENDING)
        );
        if (updated == 0) {
            return false;
        }
        publish(AccountDeletionNotificationEvent.Kind.CANCELLED,
                user, request.requestedAt(), request.coolingEndsAt(), null, detail);
        afterCommit(() -> pendingRegistry.clear(userId));
        return true;
    }

    @EventListener(ApplicationReadyEvent.class)
    @MonitoredScheduledTask(id = "account-deletion-restore", group = TaskGroup.STARTUP,
            nameKey = "admin.tasks.items.account-deletion-restore.name",
            descriptionKey = "admin.tasks.items.account-deletion-restore.description",
            scheduleType = TaskScheduleType.STARTUP)
    public void restorePendingDeletionMarkers() {
        List<PendingMarker> markers = requestMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query
                        .QueryWrapper<UserAccountDeletionRequest>()
                        .eq("status", PENDING)
        ).stream().map(request -> new PendingMarker(
                request.getUserId(),
                request.getCoolingEndsAt()
        )).toList();
        markers.forEach(marker ->
                pendingRegistry.mark(marker.userId(), marker.coolingEndsAt()));
    }

    private RequestRow lockedRequest(long userId) {
        UserAccountDeletionRequest entity = requestMapper.selectForUpdate(userId);
        return entity == null ? null : new RequestRow(
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getCoolingEndsAt(),
                entity.getCancelledAt(),
                entity.getCompletedAt(),
                entity.getCancellationReason()
        );
    }

    private User requireUser(long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new IllegalStateException("User does not exist");
        }
        return user;
    }

    private void ensureDeletableUser(User user) throws BusinessException {
        if (user.getEmail() != null && user.getEmail().endsWith("@sparkle.system")) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "System accounts cannot be deleted");
        }
    }

    private void publish(
            AccountDeletionNotificationEvent.Kind kind,
            User user,
            OffsetDateTime requestedAt,
            OffsetDateTime coolingEndsAt,
            OffsetDateTime completedAt,
            String cancellationDetail
    ) {
        eventPublisher.publishEvent(new AccountDeletionNotificationEvent(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                kind,
                requestedAt,
                coolingEndsAt,
                completedAt,
                cancellationDetail
        ));
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

    public record AccountDeletionStatus(
            String state,
            OffsetDateTime requestedAt,
            OffsetDateTime coolingEndsAt,
            OffsetDateTime completedAt,
            String cancellationReason
    ) {
        public static AccountDeletionStatus none() {
            return new AccountDeletionStatus("NONE", null, null, null, null);
        }
    }

    public record ReauthenticationIntent(
            long userId,
            long expiresAtEpochSecond
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public enum ApplicationActivityDecision {
        ALLOW,
        DELETION_CANCELLED,
        ACCOUNT_DELETED
    }

    private record RequestRow(
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime coolingEndsAt,
            OffsetDateTime cancelledAt,
            OffsetDateTime completedAt,
            String cancellationReason
    ) {
    }

    private record PendingMarker(long userId, OffsetDateTime coolingEndsAt) {
    }
}
