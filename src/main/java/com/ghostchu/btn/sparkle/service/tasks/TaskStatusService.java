package com.ghostchu.btn.sparkle.service.tasks;

import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskRunView;
import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskStatusResponse;
import com.ghostchu.btn.sparkle.controller.admin.dto.TaskStatusModels.TaskStatusView;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TaskStatusService {
    private static final int MAX_RECENT_RUNS = 8;
    private static final int MAX_MESSAGE_LENGTH = 1_000;

    private final Environment environment;
    private final ConcurrentMap<String, TaskDescriptor> descriptors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TaskState> states = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<String>> activeTaskIds = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<String>> manualTaskIds = ThreadLocal.withInitial(HashSet::new);

    public TaskStatusService(Environment environment) {
        this.environment = environment;
    }

    public void configure(MonitoredScheduledTask annotation) {
        configure(definition(annotation));
    }

    public void ensure(MonitoredScheduledTask annotation) {
        ensure(definition(annotation));
    }

    private TaskDefinition definition(MonitoredScheduledTask annotation) {
        return new TaskDefinition(
                annotation.id(),
                annotation.group(),
                TaskKind.SCHEDULED,
                annotation.nameKey(),
                annotation.descriptionKey(),
                annotation.scheduleType(),
                annotation.expression(),
                annotation.intervalMillis(),
                annotation.initialDelayMillis(),
                null,
                annotation.enabled()
        );
    }

    public void configure(TaskDefinition definition) {
        TaskDescriptor descriptor = descriptors.computeIfAbsent(
                definition.id(),
                ignored -> new TaskDescriptor(definition.id())
        );
        synchronized (descriptor) {
            boolean scheduleChanged = descriptor.scheduleType != definition.scheduleType()
                    || !java.util.Objects.equals(
                    descriptor.scheduleExpression,
                    definition.scheduleExpression()
            )
                    || descriptor.intervalMillis != definition.intervalMillis();

            descriptor.group = definition.group();
            descriptor.kind = definition.kind();
            descriptor.nameKey = definition.nameKey();
            descriptor.descriptionKey = definition.descriptionKey();
            descriptor.scheduleType = definition.scheduleType();
            descriptor.scheduleExpression = definition.scheduleExpression();
            descriptor.intervalMillis = definition.intervalMillis();
            descriptor.initialDelayMillis = definition.initialDelayMillis();
            descriptor.enabled = definition.enabled();
            descriptor.scheduleError = null;

            if (!definition.enabled()) {
                descriptor.nextRunAt = null;
                return;
            }
            if (definition.scheduleType() == TaskScheduleType.INTERVAL) {
                if (definition.initialNextRunAt() != null) {
                    descriptor.nextRunAt = definition.initialNextRunAt();
                } else if (descriptor.nextRunAt == null) {
                    descriptor.nextRunAt = Instant.now()
                            .plusMillis(definition.intervalMillis());
                }
                return;
            }
            if (definition.scheduleType() == TaskScheduleType.EVENT
                    || definition.scheduleType() == TaskScheduleType.STARTUP) {
                descriptor.nextRunAt = null;
                return;
            }
            if (!scheduleChanged && descriptor.nextRunAt != null) {
                return;
            }
            descriptor.nextRunAt = nextConfiguredRun(descriptor, Instant.now());
        }
    }

    public void ensure(TaskDefinition definition) {
        if (!descriptors.containsKey(definition.id())) {
            configure(definition);
        }
    }

    public void setEnabled(String taskId, boolean enabled) {
        TaskDescriptor descriptor = descriptors.get(taskId);
        if (descriptor == null) {
            return;
        }
        synchronized (descriptor) {
            descriptor.enabled = enabled;
            if (!enabled) {
                descriptor.nextRunAt = null;
            } else if (descriptor.nextRunAt == null) {
                descriptor.nextRunAt = nextConfiguredRun(descriptor, Instant.now());
            }
        }
    }

    public void setNextRun(String taskId, Instant nextRunAt) {
        TaskDescriptor descriptor = descriptors.get(taskId);
        if (descriptor != null) {
            descriptor.nextRunAt = descriptor.enabled ? nextRunAt : null;
        }
    }

    public void setModuleConfiguration(String taskId, String moduleId, boolean enabled) {
        TaskDescriptor descriptor = descriptors.get(taskId);
        if (descriptor != null) {
            descriptor.moduleId = moduleId;
            descriptor.moduleEnabled = enabled;
        }
    }

    public void setManualExecutable(String taskId, boolean executable) {
        TaskDescriptor descriptor = descriptors.get(taskId);
        if (descriptor != null) {
            descriptor.manualExecutable = executable;
        }
    }

    public void markManualExecution(String taskId) {
        manualTaskIds.get().add(taskId);
    }

    public String consumeScheduledTrigger(String taskId) {
        Set<String> manualOnThread = manualTaskIds.get();
        boolean manual = manualOnThread.remove(taskId);
        if (manualOnThread.isEmpty()) {
            manualTaskIds.remove();
        }
        return manual ? "MANUAL" : "SCHEDULED";
    }

    public void clearManualExecution(String taskId) {
        Set<String> manualOnThread = manualTaskIds.get();
        manualOnThread.remove(taskId);
        if (manualOnThread.isEmpty()) {
            manualTaskIds.remove();
        }
    }

    public void configurationError(String taskId, String message) {
        TaskDescriptor descriptor = descriptors.get(taskId);
        if (descriptor != null) {
            descriptor.scheduleError = sanitize(message);
            descriptor.nextRunAt = null;
        }
    }

    public TaskExecution begin(String taskId, String trigger) {
        TaskDescriptor descriptor = descriptors.get(taskId);
        if (descriptor == null) {
            return TaskExecution.noop();
        }
        Set<String> activeOnThread = activeTaskIds.get();
        if (!activeOnThread.add(taskId)) {
            return TaskExecution.noop();
        }

        Instant startedAt = Instant.now();
        String runId = taskId + ":" + System.nanoTime();
        TaskState state = states.computeIfAbsent(taskId, ignored -> new TaskState());
        state.activeRuns.put(runId, new ActiveRun(startedAt, trigger));
        updateNextRunAfterStart(descriptor, startedAt);
        return new TaskExecution(this, descriptor, state, runId, startedAt, true);
    }

    public TaskStatusResponse snapshot() {
        Instant now = Instant.now();
        List<TaskStatusView> tasks = descriptors.values().stream()
                .map(descriptor -> toView(descriptor, states.get(descriptor.id), now))
                .sorted(Comparator
                        .comparingInt((TaskStatusView task) -> groupOrder(task.group()))
                        .thenComparingInt(task -> kindOrder(task.kind()))
                        .thenComparing(TaskStatusView::nameKey))
                .toList();
        return new TaskStatusResponse(tasks, toOffsetDateTime(now));
    }

    public TaskStatusView task(String taskId) {
        TaskDescriptor descriptor = descriptors.get(taskId);
        return descriptor == null
                ? null
                : toView(descriptor, states.get(taskId), Instant.now());
    }

    private TaskStatusView toView(TaskDescriptor descriptor, TaskState state, Instant now) {
        List<Map.Entry<String, ActiveRun>> active = state == null
                ? List.of()
                : new ArrayList<>(state.activeRuns.entrySet());
        TaskRunView latest = state == null ? null : state.latestCompleted;
        ActiveRun earliestActive = active.stream()
                .map(Map.Entry::getValue)
                .min(Comparator.comparing(ActiveRun::startedAt))
                .orElse(null);
        ActiveRun latestActive = active.stream()
                .map(Map.Entry::getValue)
                .max(Comparator.comparing(ActiveRun::startedAt))
                .orElse(null);

        Instant lastStartedAt = earliestActive != null
                ? earliestActive.startedAt()
                : latest == null ? null : latest.startedAt().toInstant();
        long currentDurationMs = earliestActive == null
                ? 0
                : Math.max(0, Duration.between(earliestActive.startedAt(), now).toMillis());
        String lastTrigger = latestActive != null
                ? latestActive.trigger()
                : latest == null ? null : latest.trigger();
        List<TaskRunView> recentRuns = state == null
                ? List.of()
                : state.recentRuns();

        return new TaskStatusView(
                descriptor.id,
                descriptor.group.name(),
                descriptor.kind.name(),
                descriptor.scheduleType.name(),
                descriptor.moduleId,
                descriptor.moduleEnabled,
                descriptor.manualExecutable,
                descriptor.nameKey,
                descriptor.descriptionKey,
                scheduleLabel(descriptor),
                descriptor.enabled,
                active.size(),
                toOffsetDateTime(descriptor.nextRunAt),
                toOffsetDateTime(lastStartedAt),
                latest == null ? null : latest.finishedAt(),
                currentDurationMs,
                latest == null ? 0 : latest.durationMs(),
                lastTrigger,
                latest == null ? null : latest.result(),
                latest == null ? null : latest.message(),
                latest != null && TaskRunResult.FAILED.name().equals(latest.result())
                        ? latest.message()
                        : null,
                descriptor.scheduleError,
                recentRuns
        );
    }

    private String scheduleLabel(TaskDescriptor descriptor) {
        return switch (descriptor.scheduleType) {
            case CRON -> descriptor.scheduleExpression == null
                    || descriptor.scheduleExpression.isBlank()
                    ? "CRON"
                    : "CRON " + resolve(descriptor.scheduleExpression);
            case FIXED_RATE -> "FIXED_RATE " + humanInterval(descriptor.intervalMillis);
            case FIXED_DELAY -> "FIXED_DELAY " + humanInterval(descriptor.intervalMillis);
            case INTERVAL -> "INTERVAL " + humanInterval(descriptor.intervalMillis);
            case EVENT -> "EVENT_DRIVEN";
            case STARTUP -> "STARTUP";
        };
    }

    private String humanInterval(long intervalMillis) {
        Duration duration = Duration.ofMillis(intervalMillis);
        if (intervalMillis % 3_600_000 == 0) {
            return duration.toHours() + "h";
        }
        if (intervalMillis % 60_000 == 0) {
            return duration.toMinutes() + "m";
        }
        if (intervalMillis % 1_000 == 0) {
            return duration.toSeconds() + "s";
        }
        return duration.toMillis() + "ms";
    }

    private Instant nextConfiguredRun(TaskDescriptor descriptor, Instant now) {
        return switch (descriptor.scheduleType) {
            case CRON -> nextCronRun(descriptor, now);
            case FIXED_RATE, FIXED_DELAY ->
                    now.plusMillis(Math.max(0, descriptor.initialDelayMillis));
            case INTERVAL -> now.plusMillis(descriptor.intervalMillis);
            case EVENT, STARTUP -> null;
        };
    }

    private Instant nextCronRun(TaskDescriptor descriptor, Instant after) {
        if (descriptor.scheduleExpression == null
                || descriptor.scheduleExpression.isBlank()) {
            descriptor.scheduleError = "Cron expression is empty";
            return null;
        }
        try {
            CronExpression expression = CronExpression.parse(
                    resolve(descriptor.scheduleExpression)
            );
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime next = expression.next(after.atZone(zone));
            descriptor.scheduleError = null;
            return next == null ? null : next.toInstant();
        } catch (IllegalArgumentException exception) {
            descriptor.scheduleError = sanitize(exception.getMessage());
            return null;
        }
    }

    private void updateNextRunAfterStart(TaskDescriptor descriptor, Instant startedAt) {
        synchronized (descriptor) {
            if (!descriptor.enabled) {
                return;
            }
            if (descriptor.scheduleType == TaskScheduleType.FIXED_RATE) {
                descriptor.nextRunAt = startedAt.plusMillis(descriptor.intervalMillis);
            } else if (descriptor.scheduleType == TaskScheduleType.CRON) {
                descriptor.nextRunAt = nextCronRun(descriptor, startedAt);
            }
        }
    }

    private void complete(
            TaskDescriptor descriptor,
            TaskState state,
            String runId,
            Instant startedAt,
            TaskRunResult result,
            String message
    ) {
        ActiveRun removed = state.activeRuns.remove(runId);
        if (removed == null) {
            return;
        }
        Instant finishedAt = Instant.now();
        long durationMs = Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
        TaskRunView run = new TaskRunView(
                removed.trigger(),
                result.name(),
                toOffsetDateTime(startedAt),
                toOffsetDateTime(finishedAt),
                durationMs,
                sanitize(message)
        );
        state.record(run);

        synchronized (descriptor) {
            if (descriptor.enabled && descriptor.scheduleType == TaskScheduleType.FIXED_DELAY) {
                descriptor.nextRunAt = finishedAt.plusMillis(descriptor.intervalMillis);
            } else if (descriptor.enabled && descriptor.scheduleType == TaskScheduleType.CRON) {
                descriptor.nextRunAt = nextCronRun(descriptor, finishedAt);
            }
        }
    }

    private void releaseThreadTask(String taskId) {
        Set<String> activeOnThread = activeTaskIds.get();
        activeOnThread.remove(taskId);
        if (activeOnThread.isEmpty()) {
            activeTaskIds.remove();
        }
    }

    private String resolve(String value) {
        return value == null ? null : environment.resolvePlaceholders(value);
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.strip();
        return normalized.length() <= MAX_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null
                ? null
                : OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static int groupOrder(String group) {
        try {
            return TaskGroup.valueOf(group).ordinal();
        } catch (IllegalArgumentException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static int kindOrder(String kind) {
        try {
            return TaskKind.valueOf(kind).ordinal();
        } catch (IllegalArgumentException exception) {
            return Integer.MAX_VALUE;
        }
    }

    public static final class TaskExecution {
        private final TaskStatusService service;
        private final TaskDescriptor descriptor;
        private final TaskState state;
        private final String runId;
        private final Instant startedAt;
        private final boolean active;
        private final AtomicBoolean finished = new AtomicBoolean();

        private TaskExecution(
                TaskStatusService service,
                TaskDescriptor descriptor,
                TaskState state,
                String runId,
                Instant startedAt,
                boolean active
        ) {
            this.service = service;
            this.descriptor = descriptor;
            this.state = state;
            this.runId = runId;
            this.startedAt = startedAt;
            this.active = active;
        }

        private static TaskExecution noop() {
            return new TaskExecution(null, null, null, null, null, false);
        }

        public void success() {
            finish(TaskRunResult.SUCCESS, null);
        }

        public void success(String message) {
            finish(TaskRunResult.SUCCESS, message);
        }

        public void skipped(String reason) {
            finish(TaskRunResult.SKIPPED, reason);
        }

        public void failure(Throwable throwable) {
            String message = throwable == null
                    ? null
                    : throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
            finish(TaskRunResult.FAILED, message);
        }

        public void failure(String message) {
            finish(TaskRunResult.FAILED, message);
        }

        private void finish(TaskRunResult result, String message) {
            if (!active || !finished.compareAndSet(false, true)) {
                return;
            }
            try {
                service.complete(
                        descriptor,
                        state,
                        runId,
                        startedAt,
                        result,
                        message
                );
            } finally {
                service.releaseThreadTask(descriptor.id);
            }
        }
    }

    private static final class TaskDescriptor {
        private final String id;
        private volatile TaskGroup group = TaskGroup.MAINTENANCE;
        private volatile TaskKind kind = TaskKind.SCHEDULED;
        private volatile String nameKey = "";
        private volatile String descriptionKey = "";
        private volatile TaskScheduleType scheduleType = TaskScheduleType.EVENT;
        private volatile String moduleId;
        private volatile Boolean moduleEnabled;
        private volatile boolean manualExecutable;
        private volatile String scheduleExpression;
        private volatile long intervalMillis;
        private volatile long initialDelayMillis;
        private volatile Instant nextRunAt;
        private volatile boolean enabled = true;
        private volatile String scheduleError;

        private TaskDescriptor(String id) {
            this.id = id;
        }
    }

    private static final class TaskState {
        private final ConcurrentMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();
        private final Deque<TaskRunView> completedRuns = new ArrayDeque<>();
        private volatile TaskRunView latestCompleted;

        private synchronized void record(TaskRunView run) {
            completedRuns.addFirst(run);
            while (completedRuns.size() > MAX_RECENT_RUNS) {
                completedRuns.removeLast();
            }
            latestCompleted = run;
        }

        private synchronized List<TaskRunView> recentRuns() {
            return List.copyOf(completedRuns);
        }

    }

    private record ActiveRun(Instant startedAt, String trigger) {
    }
}
