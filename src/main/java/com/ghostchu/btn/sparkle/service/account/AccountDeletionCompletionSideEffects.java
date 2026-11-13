package com.ghostchu.btn.sparkle.service.account;

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
    public void afterCompletion(AccountDeletionNotificationEvent event) {
        if (event == null
                || event.kind() != AccountDeletionNotificationEvent.Kind.COMPLETED) {
            return;
        }
        try {
            blurredAvatarDiskCache.refreshOnLogin(event.userId());
        } catch (Exception exception) {
            log.warn("Unable to clear avatar cache for deleted user {}: {}",
                    event.userId(), exception.getMessage());
        }
        try {
            var sessions = sessionRepository.findByPrincipalName(event.nickname());
            sessions.keySet().forEach(sessionRepository::deleteById);
        } catch (Exception exception) {
            log.warn("Unable to revoke sessions for deleted user {}: {}",
                    event.userId(), exception.getMessage());
        }
    }
}
