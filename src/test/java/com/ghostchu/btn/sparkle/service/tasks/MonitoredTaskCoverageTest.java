package com.ghostchu.btn.sparkle.service.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MonitoredTaskCoverageTest {
    @Test
    void startupAndEventCallsHaveTheirOwnTriggerAndNoSchedule() {
        TaskStatusService tasks = new TaskStatusService(new MockEnvironment());
        Sample service = proxy(tasks);
        service.start();
        service.event();
        var startup = tasks.snapshot().tasks().stream().filter(t -> t.id().equals("startup")).findFirst().orElseThrow();
        assertEquals("STARTUP", startup.lastTrigger());
        assertEquals("ASYNC", startup.kind());
        assertNull(startup.nextRunAt());
        var event = tasks.snapshot().tasks().stream().filter(t -> t.id().equals("event")).findFirst().orElseThrow();
        assertEquals("EVENT", event.lastTrigger());
        assertEquals("FAILED", event.lastResult());
        service.event();
        assertEquals(0, tasks.snapshot().tasks().getFirst().runningCount());
    }

    @Test
    void intervalMetadataUsesTheSamePropertiesAsTheScheduler() {
        TaskStatusService tasks = new TaskStatusService(new MockEnvironment()
                .withProperty("worker.delay", "17000").withProperty("worker.initial", "23000"));
        proxy(tasks).poll();
        var task = tasks.snapshot().tasks().getFirst();
        assertEquals("FIXED_DELAY 17s", task.schedule());
        assertNotNull(task.nextRunAt());
        assertEquals("SKIPPED", task.lastResult());
    }

    @Test
    void allSpringScheduledEntriesAreMonitored() throws Exception {
        int count = 0;
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path).replaceAll("(?m)^\\s*//.*$", "");
                var entries = java.util.regex.Pattern.compile("@Scheduled\\s*\\([\\s\\S]*?(?=public |protected |private |void )").matcher(source);
                while (entries.find()) {
                    count++;
                    assertTrue(entries.group().contains("MonitoredScheduledTask"), path.toString());
                }
            }
        }
        assertTrue(count > 10, "Must discover the actual scheduled entry points");
    }

    @Test
    void nestedHandledFailuresDoNotLeakIntoOuterOrLaterCalls() {
        TaskStatusService tasks = new TaskStatusService(new MockEnvironment());
        tasks.ensure(TaskDefinition.event("outer", TaskGroup.MAINTENANCE, "outer", ""));
        tasks.ensure(TaskDefinition.event("inner", TaskGroup.MAINTENANCE, "inner", ""));
        var outerExecution = tasks.begin("outer", "EVENT");
        try (var outer = new MonitoredTaskOutcome()) {
            var innerExecution = tasks.begin("inner", "EVENT");
            try (var inner = new MonitoredTaskOutcome()) {
                MonitoredTaskOutcome.failure("Handled failure");
                inner.finish(innerExecution);
            }
            outer.finish(outerExecution);
        }
        assertEquals("SUCCESS", tasks.task("outer").lastResult());
        assertEquals("FAILED", tasks.task("inner").lastResult());
        proxy(tasks).start();
        assertEquals("SUCCESS", tasks.task("startup").lastResult());
    }

    private Sample proxy(TaskStatusService tasks) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Sample());
        factory.addAspect(new MonitoredScheduledTaskAspect(tasks));
        return factory.getProxy();
    }

    static class Sample {
        @MonitoredScheduledTask(id = "startup", group = TaskGroup.STARTUP,
                nameKey = "startup", scheduleType = TaskScheduleType.STARTUP)
        public void start() { }

        @MonitoredScheduledTask(id = "event", group = TaskGroup.MAINTENANCE,
                nameKey = "event", scheduleType = TaskScheduleType.EVENT)
        public void event() {
            MonitoredTaskOutcome.failure("Handled failure");
            MonitoredTaskOutcome.skipped("Must not hide the failure");
        }

        @MonitoredScheduledTask(id = "poll", group = TaskGroup.MAINTENANCE,
                nameKey = "poll", scheduleType = TaskScheduleType.FIXED_DELAY,
                intervalExpression = "${worker.delay:5000}",
                initialDelayExpression = "${worker.initial:10000}")
        public void poll() { MonitoredTaskOutcome.skipped("Nothing to process"); }
    }
}
