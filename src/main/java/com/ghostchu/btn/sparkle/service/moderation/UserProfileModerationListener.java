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
        if (event.nicknameChanged()) {
            enqueue(event.userId(), "nickname", () -> moderationService.enqueueUsername(
                    event.userId(), event.nicknameGeneration(), event.nickname()));
        }
        if (event.avatarChanged()) {
            enqueue(event.userId(), "avatar", () -> moderationService.enqueueAvatar(
                    event.userId(), event.avatarGeneration(), event.avatarUrl(), event.nickname()));
        }
    }

    private void enqueue(long userId, String field, Runnable review) {
        try {
            review.run();
        } catch (Exception exception) {
            MonitoredTaskOutcome.failure("One or more operations failed; see server logs");
            log.error("Unable to enqueue {} moderation for user {}", field, userId, exception);
        }
    }
}
