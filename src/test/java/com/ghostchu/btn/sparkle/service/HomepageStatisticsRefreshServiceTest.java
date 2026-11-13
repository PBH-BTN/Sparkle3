package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.impl.StatisticsRefreshServiceImpl;
import com.ghostchu.btn.sparkle.service.tasks.TaskManualExecutionService;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomepageStatisticsRefreshServiceTest {
    @Test
    void homepageVisibilityMatchesEveryModuleSwitch() {
        DatabaseConfigService config = mock(DatabaseConfigService.class);
        when(config.getRawValues(anyList())).thenReturn(Map.of(
                "sparkle.homepage-statistics-refresh.modules.ban-history.enabled", "false",
                "sparkle.homepage-statistics-refresh.modules.network-charts.enabled", "false",
                "sparkle.homepage-statistics-refresh.modules.companion-charts.enabled", "false"
        ));
        HomepageStatisticsRefreshService service = new HomepageStatisticsRefreshService(
                mock(StatisticsRefreshServiceImpl.class), mock(HomeStatisticsService.class),
                mock(BittorrentTrafficStatisticsService.class), mock(BittorrentTrafficFeatureService.class),
                config, mock(TaskStatusService.class), mock(TaskManualExecutionService.class));

        assertEquals(Map.of(
                "ban-history", false, "active-apps", true, "swarm-tracker", true,
                "torrents", true, "network-charts", false, "companion-charts", false,
                "bittorrent-traffic", true
        ), service.enabledModules());
    }

    @Test
    void manualRefreshRejectsDisabledBitTorrentTraffic() throws BusinessException {
        StatisticsRefreshServiceImpl statisticsRefreshService =
                mock(StatisticsRefreshServiceImpl.class);
        HomeStatisticsService homeStatisticsService = mock(HomeStatisticsService.class);
        BittorrentTrafficStatisticsService trafficStatisticsService =
                mock(BittorrentTrafficStatisticsService.class);
        BittorrentTrafficFeatureService featureService =
                mock(BittorrentTrafficFeatureService.class);
        DatabaseConfigService databaseConfigService = mock(DatabaseConfigService.class);
        when(databaseConfigService.getRawValues(anyList())).thenReturn(
                Map.of(BittorrentTrafficFeatureService.ENABLED_CONFIG_KEY, "false")
        );
        when(featureService.isEnabled()).thenReturn(false);
        HomepageStatisticsRefreshService refreshService =
                new HomepageStatisticsRefreshService(
                        statisticsRefreshService,
                        homeStatisticsService,
                        trafficStatisticsService,
                        featureService,
                        databaseConfigService,
                        mock(TaskStatusService.class),
                        mock(TaskManualExecutionService.class)
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> refreshService.requestRefresh("bittorrent-traffic", null)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(trafficStatisticsService, never()).refresh();
    }
}
