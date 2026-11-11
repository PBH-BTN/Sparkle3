package com.ghostchu.btn.sparkle.service.tasks;

import java.time.Instant;

public record TaskDefinition(
        String id,
        TaskGroup group,
        TaskKind kind,
        String nameKey,
        String descriptionKey,
        TaskScheduleType scheduleType,
        String scheduleExpression,
        long intervalMillis,
        long initialDelayMillis,
        Instant initialNextRunAt,
        boolean enabled
) {
    public TaskDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (group == null || kind == null || scheduleType == null) {
            throw new IllegalArgumentException("Task group, kind, and schedule type are required");
        }
        if (nameKey == null || nameKey.isBlank()) {
            throw new IllegalArgumentException("Task name key is required");
        }
        if ((scheduleType == TaskScheduleType.INTERVAL
                || scheduleType == TaskScheduleType.FIXED_RATE
                || scheduleType == TaskScheduleType.FIXED_DELAY)
                && intervalMillis <= 0) {
            throw new IllegalArgumentException("Task interval must be positive");
        }
    }

    public static TaskDefinition cron(
            String id,
            TaskGroup group,
            TaskKind kind,
            String nameKey,
            String descriptionKey,
            String expression,
            boolean enabled
    ) {
        return new TaskDefinition(
                id,
                group,
                kind,
                nameKey,
                descriptionKey,
                TaskScheduleType.CRON,
                expression,
                0,
                0,
                null,
                enabled
        );
    }

    public static TaskDefinition fixedRate(
            String id,
            TaskGroup group,
            TaskKind kind,
            String nameKey,
            String descriptionKey,
            long intervalMillis,
            long initialDelayMillis,
            boolean enabled
    ) {
        return new TaskDefinition(
                id,
                group,
                kind,
                nameKey,
                descriptionKey,
                TaskScheduleType.FIXED_RATE,
                null,
                intervalMillis,
                initialDelayMillis,
                null,
                enabled
        );
    }

    public static TaskDefinition fixedDelay(
            String id,
            TaskGroup group,
            TaskKind kind,
            String nameKey,
            String descriptionKey,
            long intervalMillis,
            long initialDelayMillis,
            boolean enabled
    ) {
        return new TaskDefinition(
                id,
                group,
                kind,
                nameKey,
                descriptionKey,
                TaskScheduleType.FIXED_DELAY,
                null,
                intervalMillis,
                initialDelayMillis,
                null,
                enabled
        );
    }

    public static TaskDefinition interval(
            String id,
            TaskGroup group,
            TaskKind kind,
            String nameKey,
            String descriptionKey,
            long intervalMillis,
            Instant nextRunAt,
            boolean enabled
    ) {
        return new TaskDefinition(
                id,
                group,
                kind,
                nameKey,
                descriptionKey,
                TaskScheduleType.INTERVAL,
                null,
                intervalMillis,
                0,
                nextRunAt,
                enabled
        );
    }

    public static TaskDefinition event(
            String id,
            TaskGroup group,
            String nameKey,
            String descriptionKey
    ) {
        return new TaskDefinition(
                id,
                group,
                TaskKind.ASYNC,
                nameKey,
                descriptionKey,
                TaskScheduleType.EVENT,
                null,
                0,
                0,
                null,
                true
        );
    }

    public static TaskDefinition startup(
            String id,
            TaskGroup group,
            String nameKey,
            String descriptionKey
    ) {
        return new TaskDefinition(
                id,
                group,
                TaskKind.ASYNC,
                nameKey,
                descriptionKey,
                TaskScheduleType.STARTUP,
                null,
                0,
                0,
                null,
                true
        );
    }
}
