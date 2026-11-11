package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;
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
                executionService
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
                executionService
        );

        assertThrows(BusinessException.class, () -> scheduler.triggerNow("disabled-test"));
    }
}
