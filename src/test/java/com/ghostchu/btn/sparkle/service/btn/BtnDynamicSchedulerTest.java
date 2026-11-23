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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BtnDynamicSchedulerTest {
    @Test
    void schedulesHeartbeatCleanupWithoutAQueueFlush() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        BtnRuntimeConfigService configService = mock(BtnRuntimeConfigService.class);
        UserappsHeartbeatServiceImpl heartbeatService =
                mock(UserappsHeartbeatServiceImpl.class);
        GithubSyncSettingsService githubSyncSettingsService =
                mock(GithubSyncSettingsService.class);
        TaskStatusService taskStatusService = mock(TaskStatusService.class);
        when(configService.text(anyString(), anyString())).thenReturn("-");
        when(githubSyncSettingsService.schedule()).thenReturn("-");
        when(configService.text("heartbeat", "cleanupCron")).thenReturn("0 */10 * * * *");
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

        verify(taskScheduler, never()).scheduleWithFixedDelay(
                any(Runnable.class), any(Instant.class), any(Duration.class));
        ArgumentCaptor<TaskDefinition> definitions =
                ArgumentCaptor.forClass(TaskDefinition.class);
        verify(taskStatusService, atLeastOnce()).configure(definitions.capture());
        TaskDefinition heartbeat = definitions.getAllValues().stream()
                .filter(definition -> "heartbeat-cleanup".equals(definition.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(TaskScheduleType.CRON, heartbeat.scheduleType());
    }
}
