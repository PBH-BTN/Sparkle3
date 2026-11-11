package com.ghostchu.btn.sparkle.service.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskManualExecutionServiceTest {
    @Test
    void annotatedScheduledTaskCanRunManuallyAndIsRecordedAsManual() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            TaskManualExecutionService manualExecutionService =
                    context.getBean(TaskManualExecutionService.class);
            TaskStatusService taskStatusService = context.getBean(TaskStatusService.class);

            var response = manualExecutionService.trigger("manual-execution-test");

            assertTrue(response.started());
            assertTrue(ExampleScheduledTask.STARTED.await(2, TimeUnit.SECONDS));
            waitUntilFinished(taskStatusService);
            var status = taskStatusService.task("manual-execution-test");
            assertTrue(status.manualExecutable());
            assertEquals("MANUAL", status.lastTrigger());
            assertEquals("SUCCESS", status.lastResult());
        }
    }

    private void waitUntilFinished(TaskStatusService taskStatusService)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        while (Instant.now().isBefore(deadline)) {
            var status = taskStatusService.task("manual-execution-test");
            if (status != null && status.lastResult() != null && status.runningCount() == 0) {
                return;
            }
            Thread.sleep(20);
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        TaskStatusService taskStatusService() {
            return new TaskStatusService(new MockEnvironment());
        }

        @Bean
        MonitoredScheduledTaskAspect monitoredScheduledTaskAspect(
                TaskStatusService taskStatusService
        ) {
            return new MonitoredScheduledTaskAspect(taskStatusService);
        }

        @Bean
        TaskManualExecutionService taskManualExecutionService(
                org.springframework.context.ApplicationContext applicationContext,
                TaskStatusService taskStatusService
        ) {
            return new TaskManualExecutionService(applicationContext, taskStatusService);
        }

        @Bean
        ExampleScheduledTask exampleScheduledTask() {
            return new ExampleScheduledTask();
        }
    }

    @Service
    public static class ExampleScheduledTask {
        private static final CountDownLatch STARTED = new CountDownLatch(1);

        @MonitoredScheduledTask(
                id = "manual-execution-test",
                group = TaskGroup.MAINTENANCE,
                nameKey = "test.manual-execution.name",
                descriptionKey = "test.manual-execution.description",
                scheduleType = TaskScheduleType.FIXED_DELAY,
                intervalMillis = 60_000
        )
        public void run() {
            STARTED.countDown();
        }
    }
}
