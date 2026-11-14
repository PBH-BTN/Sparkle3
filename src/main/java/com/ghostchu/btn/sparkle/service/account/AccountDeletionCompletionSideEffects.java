package com.ghostchu.btn.sparkle.service.account;

import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.service.BlurredAvatarDiskCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionCompletionSideEffects {
    private final BlurredAvatarDiskCache blurredAvatarDiskCache;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    @MonitoredScheduledTask(id = "account-deletion-cleanup", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.account-deletion-cleanup.name",
            descriptionKey = "admin.tasks.items.account-deletion-cleanup.description",
            scheduleType = TaskScheduleType.EVENT)
    public void afterCompletion(AccountDeletionNotificationEvent event) {
        if (event == null
                || event.kind() != AccountDeletionNotificationEvent.Kind.COMPLETED) {
            MonitoredTaskOutcome.skipped("Account deletion is not completed");
            return;
        }
        try {
            blurredAvatarDiskCache.refreshOnLogin(event.userId());
        } catch (Exception exception) {
            MonitoredTaskOutcome.failure("One or more operations failed; see server logs");
            log.warn("Unable to clear avatar cache for deleted user {}: {}",
                    event.userId(), exception.getMessage());
        }
        try {
            var sessions = sessionRepository.findByPrincipalName(event.nickname());
            sessions.keySet().forEach(sessionRepository::deleteById);
        } catch (Exception exception) {
            MonitoredTaskOutcome.failure("One or more operations failed; see server logs");
            log.warn("Unable to revoke sessions for deleted user {}: {}",
                    event.userId(), exception.getMessage());
        }
    }
}
