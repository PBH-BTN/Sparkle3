package com.ghostchu.btn.sparkle.service.moderation;

import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileModerationListener {
    private final AiModerationService moderationService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    @MonitoredScheduledTask(id = "profile-moderation-enqueue", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.profile-moderation-enqueue.name",
            descriptionKey = "admin.tasks.items.profile-moderation-enqueue.description",
            scheduleType = TaskScheduleType.EVENT)
    public void reviewChangedProfile(UserProfileChangedEvent event) {
        try {
            if (event.nicknameChanged()) {
                moderationService.enqueueUsername(
                        event.userId(),
                        event.nicknameGeneration(),
                        event.nickname()
                );
            }
            if (event.avatarChanged()) {
                moderationService.enqueueAvatar(
                        event.userId(),
                        event.avatarGeneration(),
                        event.avatarUrl(),
                        event.nickname()
                );
            }
        } catch (Exception exception) {
            MonitoredTaskOutcome.failure("One or more operations failed; see server logs");
            log.error("Unable to enqueue profile moderation for user {}: {}",
                    event.userId(), exception.getMessage(), exception);
        }
    }
}
