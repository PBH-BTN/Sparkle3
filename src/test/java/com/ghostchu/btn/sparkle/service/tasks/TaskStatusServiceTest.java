package com.ghostchu.btn.sparkle.service.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskStatusServiceTest {
    @Test
    void recordsRunningStateAndRecentResults() {
        TaskStatusService service = new TaskStatusService(new MockEnvironment());
        service.configure(TaskDefinition.fixedRate(
                "sample-task",
                TaskGroup.MAINTENANCE,
                TaskKind.SCHEDULED,
                "admin.tasks.items.sample-task.name",
                "admin.tasks.items.sample-task.description",
                60_000,
                0,
                true
        ));

        TaskStatusService.TaskExecution running = service.begin(
                "sample-task",
                "MANUAL"
        );
        var whileRunning = service.snapshot().tasks().getFirst();
        assertEquals(1, whileRunning.runningCount());
        assertEquals("MANUAL", whileRunning.lastTrigger());
        assertNotNull(whileRunning.nextRunAt());

        running.success("Completed");
        for (int index = 0; index < 10; index++) {
            TaskStatusService.TaskExecution execution = service.begin(
                    "sample-task",
                    "SCHEDULED"
            );
            execution.success("Run " + index);
        }

        var completed = service.snapshot().tasks().getFirst();
        assertEquals(0, completed.runningCount());
        assertEquals(TaskRunResult.SUCCESS.name(), completed.lastResult());
        assertEquals(8, completed.recentRuns().size());
        assertEquals("Run 9", completed.recentRuns().getFirst().message());
    }

    @Test
    void resolvesCronPlaceholdersAndDisablesNextRun() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("test.schedule", "0 */5 * * * *");
        TaskStatusService service = new TaskStatusService(environment);
        service.configure(TaskDefinition.cron(
                "cron-task",
                TaskGroup.ANALYSIS,
                TaskKind.SCHEDULED,
                "admin.tasks.items.cron-task.name",
                "admin.tasks.items.cron-task.description",
                "${test.schedule}",
                true
        ));

        assertNotNull(service.snapshot().tasks().getFirst().nextRunAt());
        service.setEnabled("cron-task", false);
        assertFalse(service.snapshot().tasks().getFirst().enabled());
        assertNull(service.snapshot().tasks().getFirst().nextRunAt());
    }
}
