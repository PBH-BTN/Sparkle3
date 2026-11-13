package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.service.impl.BanHistoryServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.GithubSyncServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.SwarmTrackerServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.UserIPWarningServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.UserappsHeartbeatServiceImpl;
import com.ghostchu.btn.sparkle.service.github.GithubSyncSettingsService;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import com.ghostchu.btn.sparkle.util.GlobalpingApiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BtnDynamicSchedulerTest {
    @Test
    @SuppressWarnings("unchecked")
    void heartbeatFlushUsesFixedDelayAndDoesNotCatchUpMissedRuns() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        BtnRuntimeConfigService configService = mock(BtnRuntimeConfigService.class);
        UserappsHeartbeatServiceImpl heartbeatService =
                mock(UserappsHeartbeatServiceImpl.class);
        GithubSyncSettingsService githubSyncSettingsService =
                mock(GithubSyncSettingsService.class);
        TaskStatusService taskStatusService = mock(TaskStatusService.class);
        when(configService.longValue("heartbeatQueue", "flushIntervalMs"))
                .thenReturn(5_000L);
        when(configService.longValue("heartbeatQueue", "initialDelayMs"))
                .thenReturn(1_000L);
        when(configService.text(anyString(), anyString())).thenReturn("-");
        when(githubSyncSettingsService.schedule()).thenReturn("-");
        when(taskScheduler.scheduleWithFixedDelay(
                any(Runnable.class),
                any(Instant.class),
                any(Duration.class)
        )).thenReturn(mock(ScheduledFuture.class));
        BtnDynamicScheduler scheduler = new BtnDynamicScheduler(
                taskScheduler,
                configService,
                heartbeatService,
                mock(SwarmTrackerServiceImpl.class),
                mock(UserIPWarningServiceImpl.class),
                mock(GithubSyncServiceImpl.class),
                githubSyncSettingsService,
                mock(BanHistoryServiceImpl.class),
                mock(GlobalpingApiClient.class),
                taskStatusService
        );

        scheduler.refreshSchedules();

        verify(taskScheduler).scheduleWithFixedDelay(
                any(Runnable.class),
                any(Instant.class),
                eq(Duration.ofMillis(5_000))
        );
        verify(taskScheduler, never()).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                any(Duration.class)
        );
        ArgumentCaptor<TaskDefinition> definitions =
                ArgumentCaptor.forClass(TaskDefinition.class);
        verify(taskStatusService, atLeastOnce()).configure(definitions.capture());
        TaskDefinition heartbeat = definitions.getAllValues().stream()
                .filter(definition -> "heartbeat-queue-flush".equals(definition.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(TaskScheduleType.FIXED_DELAY, heartbeat.scheduleType());
        assertEquals(5_000, heartbeat.intervalMillis());
    }
}
