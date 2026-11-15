package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.impl.StatisticsRefreshServiceImpl;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskManualExecutionService;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomepageStatisticsRefreshServiceTest {
    @Test
    void homepageVisibilityMatchesEveryModuleSwitch() {
        DatabaseConfigService config = mock(DatabaseConfigService.class);
        when(config.getRawValues(anyList())).thenReturn(Map.of(
                "sparkle.homepage-statistics-refresh.modules.ban-history.enabled", "false",
                "sparkle.homepage-statistics-refresh.modules.network-charts.enabled", "false",
                "sparkle.homepage-statistics-refresh.modules.companion-charts.enabled", "false",
                "sparkle.homepage-statistics-refresh.modules.ip-flags.enabled", "false"
        ));
        var tasks = mock(TaskStatusService.class);
        HomepageStatisticsRefreshService service = new HomepageStatisticsRefreshService(
                mock(StatisticsRefreshServiceImpl.class), mock(HomeStatisticsService.class),
                mock(HomepageIpFlagService.class), config, tasks, mock(TaskManualExecutionService.class));

        assertEquals(Map.of(
                "ban-history", false, "active-apps", true, "swarm-tracker", true,
                "torrents", true, "network-charts", false, "companion-charts", false, "ip-flags", false
        ), service.enabledModules());
        assertFalse(service.isIpFlagsEnabled());
        service.runScheduledRefreshes();
        verify(tasks).configure(argThat((TaskDefinition task) ->
                task.id().equals("homepage-statistics-ip-flags") && !task.enabled()));
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.requestRefresh("ip-flags", null)).getStatusCode());
    }

    @Test
    void removedTrafficModuleCannotBeRefreshedOrConfigured() throws Exception {
        DatabaseConfigService config = mock(DatabaseConfigService.class);
        when(config.getRawValues(anyList())).thenReturn(Map.of());
        HomepageStatisticsRefreshService service = new HomepageStatisticsRefreshService(
                mock(StatisticsRefreshServiceImpl.class), mock(HomeStatisticsService.class),
                mock(HomepageIpFlagService.class), config, mock(TaskStatusService.class),
                mock(TaskManualExecutionService.class));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.requestRefresh("bittorrent-traffic", null)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.updateConfig(new com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.ConfigUpdate(
                        java.util.List.of(new com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.ModuleConfig(
                                "bittorrent-traffic", true, 60))), null)).getStatusCode());
    }
}
