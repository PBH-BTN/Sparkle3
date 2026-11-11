package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class TaskStatusModels {
    private TaskStatusModels() {
    }

    public record TaskRunView(
            String trigger,
            String result,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            long durationMs,
            String message
    ) {
    }

    public record TaskStatusView(
            String id,
            String group,
            String kind,
            String nameKey,
            String descriptionKey,
            String schedule,
            boolean enabled,
            int runningCount,
            OffsetDateTime nextRunAt,
            OffsetDateTime lastStartedAt,
            OffsetDateTime lastFinishedAt,
            long currentDurationMs,
            long lastDurationMs,
            String lastTrigger,
            String lastResult,
            String lastMessage,
            String lastError,
            String scheduleError,
            List<TaskRunView> recentRuns
    ) {
    }

    public record TaskStatusResponse(
            List<TaskStatusView> tasks,
            OffsetDateTime generatedAt
    ) {
    }
}
