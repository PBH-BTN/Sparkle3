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
