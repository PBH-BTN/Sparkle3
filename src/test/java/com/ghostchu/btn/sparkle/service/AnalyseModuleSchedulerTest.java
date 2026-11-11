package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.points.PointToggleService;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyseModuleSchedulerTest {
    @Test
    void manualTriggerRunsEnabledModuleAsynchronously() throws Exception {
        AnalyseModuleConfigService configService =
                mock(AnalyseModuleConfigService.class);
        AnalyseModuleExecutionService executionService =
                mock(AnalyseModuleExecutionService.class);
        CountDownLatch started = new CountDownLatch(1);
        when(executionService.runner("manual-test")).thenReturn(started::countDown);
        when(configService.isEnabled("manual-test")).thenReturn(true);
        AnalyseModuleScheduler scheduler = new AnalyseModuleScheduler(
                mock(TaskScheduler.class),
                configService,
                executionService,
                mock(PointToggleService.class),
                new TaskStatusService(new MockEnvironment())
        );

        scheduler.triggerNow("manual-test");

        assertTrue(started.await(2, TimeUnit.SECONDS));
    }

    @Test
    void manualTriggerRejectsDisabledModule() throws Exception {
        AnalyseModuleConfigService configService =
                mock(AnalyseModuleConfigService.class);
        AnalyseModuleExecutionService executionService =
                mock(AnalyseModuleExecutionService.class);
        when(executionService.runner("disabled-test")).thenReturn(() -> {
        });
        when(configService.isEnabled("disabled-test")).thenReturn(false);
        AnalyseModuleScheduler scheduler = new AnalyseModuleScheduler(
                mock(TaskScheduler.class),
                configService,
                executionService,
                mock(PointToggleService.class),
                new TaskStatusService(new MockEnvironment())
        );

        assertThrows(BusinessException.class, () -> scheduler.triggerNow("disabled-test"));
    }
}
