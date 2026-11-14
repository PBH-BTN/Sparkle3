package com.ghostchu.btn.sparkle.service.tasks;

/** Reports handled failures without changing the business entry point's exception policy. */
public final class MonitoredTaskOutcome implements AutoCloseable {
    private static final ThreadLocal<MonitoredTaskOutcome> CURRENT = new ThreadLocal<>();
    private final MonitoredTaskOutcome previous;
    private String failure;
    private String skipped;

    MonitoredTaskOutcome() {
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static void failure(String message) {
        MonitoredTaskOutcome current = CURRENT.get();
        if (current != null) {
            current.failure = message;
        }
    }

    public static void skipped(String message) {
        MonitoredTaskOutcome current = CURRENT.get();
        if (current != null) {
            current.skipped = message;
        }
    }

    void finish(TaskStatusService.TaskExecution execution) {
        if (failure != null) {
            execution.failure(new IllegalStateException(failure));
        } else if (skipped != null) {
            execution.skipped(skipped);
        } else {
            execution.success();
        }
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
