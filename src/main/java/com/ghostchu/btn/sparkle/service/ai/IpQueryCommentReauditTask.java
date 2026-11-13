package com.ghostchu.btn.sparkle.service.ai;

import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the IP comment moderation queue moving: comments that were never
 * reviewed, or whose review failed while the model was unavailable, are put
 * back into the queue. Comments with an active review job are left untouched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IpQueryCommentReauditTask {
    private static final int BATCH_SIZE = 50;

    private final AiModerationService moderationService;

    @Scheduled(cron = "${sparkle.ai.moderation.comment-reaudit-cron:0 */30 * * * *}")
    @MonitoredScheduledTask(
            id = "ip-query-comment-reaudit",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.ip-query-comment-reaudit.name",
            descriptionKey = "admin.tasks.items.ip-query-comment-reaudit.description",
            expression = "${sparkle.ai.moderation.comment-reaudit-cron:0 */30 * * * *}"
    )
    public void reauditOutstandingComments() {
        if (!moderationService.enabled()) {
            log.debug("Skipping IP comment re-audit because AI moderation is disabled");
            return;
        }
        int queued = moderationService.reauditOutstandingComments(BATCH_SIZE);
        if (queued > 0) {
            log.info("Queued {} IP query comments for AI re-audit", queued);
        }
    }
}
