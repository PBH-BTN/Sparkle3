package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BittorrentTrafficStatisticsServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void disabledFeatureSkipsReadsAndRefreshes() throws BusinessException {
        BittorrentTrafficMapper mapper = mock(BittorrentTrafficMapper.class);
        BittorrentTrafficSnapshotService snapshotService =
                mock(BittorrentTrafficSnapshotService.class);
        BittorrentTrafficMapper trafficMapper = mock(BittorrentTrafficMapper.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        BittorrentTrafficFeatureService featureService =
                mock(BittorrentTrafficFeatureService.class);
        when(featureService.isEnabled()).thenReturn(false);
        BittorrentTrafficStatisticsService statisticsService =
                new BittorrentTrafficStatisticsService(
                        mapper,
                        snapshotService,
                        trafficMapper,
                        redisTemplate,
                        new ObjectMapper(),
                        featureService
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> statisticsService.getStatistics(7)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        statisticsService.refresh();

        verifyNoInteractions(mapper, snapshotService, trafficMapper, redisTemplate);
    }
}
