package com.ghostchu.btn.sparkle.service.ai;

import com.ghostchu.btn.sparkle.entity.AiModerationJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiModerationWorker {
    private final AiModerationService moderationService;

    @Scheduled(
            fixedDelayString = "${sparkle.ai.moderation.worker-delay-ms:5000}",
            initialDelayString = "${sparkle.ai.moderation.worker-initial-delay-ms:10000}"
    )
    public void processNext() {
        for (int processed = 0; processed < 5; processed++) {
            Optional<AiModerationJob> claimed = moderationService.claimNext();
            if (claimed.isEmpty()) {
                return;
            }
            AiModerationJob job = claimed.get();
            try {
                moderationService.complete(job, moderationService.review(job));
            } catch (Exception exception) {
                moderationService.fail(job, exception);
                if (moderationService.enabled()) {
                    log.debug("AI moderation request failed for job {}: {}",
                            job.getId(), exception.getMessage());
                }
            }
        }
    }
}
