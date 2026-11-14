package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.service.dto.DashboardTrafficRowDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
                        mapper, snapshotService, trafficMapper, redisTemplate,
                        new ObjectMapper(), featureService);
        BusinessException exception = assertThrows(
                BusinessException.class, () -> statisticsService.getStatistics(7));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        statisticsService.refresh();
        verifyNoInteractions(mapper, snapshotService, trafficMapper, redisTemplate);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void emptyAggregateRowsProduceZeroTrafficWithoutFallback(boolean snapshot) throws Exception {
        var mapper = mock(BittorrentTrafficMapper.class);
        when(mapper.relationReadable(anyString())).thenReturn(snapshot);
        when(mapper.selectBanTraffic(any(), any(), anyInt(), anyInt(), eq(snapshot)))
                .thenReturn(Collections.singletonList(null));
        when(mapper.selectSwarmTraffic(any(), any(), anyInt(), eq(snapshot)))
                .thenReturn(Collections.singletonList(null));

        var service = service(mapper);
        service.refresh();
        var response = service.getStatistics(7);

        assertEquals("0", response.totals().uploadBytes());
        assertEquals("0", response.totals().downloadBytes());
        assertEquals("0", response.totals().transferBytes());
        assertTrue(response.topUploadCities().data().isEmpty());
        assertTrue(response.topDownloadCities().data().isEmpty());
        verify(mapper).selectBanTraffic(any(), any(), eq(7), eq(30), eq(snapshot));
        verify(mapper, times(3)).selectSwarmTraffic(any(), any(), eq(30), eq(snapshot));
        verify(mapper, never()).selectBanTraffic(any(), any(), anyInt(), anyInt(), eq(!snapshot));
        verify(mapper, never()).selectSwarmTraffic(any(), any(), anyInt(), eq(!snapshot));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nullRowsDoNotDiscardValidTotalsOrCities(boolean snapshot) throws Exception {
        var mapper = mock(BittorrentTrafficMapper.class);
        when(mapper.relationReadable(anyString())).thenReturn(snapshot);
        var total = new DashboardTrafficRowDto();
        total.setUploadBytes("9007199254740993");
        total.setDownloadBytes("2");
        var city = new DashboardTrafficRowDto();
        city.setCity("Test City");
        city.setUploadBytes("9007199254740993");
        city.setDownloadBytes("2");
        when(mapper.selectBanTraffic(any(), any(), anyInt(), anyInt(), eq(snapshot)))
                .thenReturn(Collections.singletonList(null));
        when(mapper.selectSwarmTraffic(any(), any(), anyInt(), eq(snapshot)))
                .thenReturn(Arrays.asList(null, total, null, city));

        var service = service(mapper);
        service.refresh();
        var response = service.getStatistics(7);

        assertEquals("9007199254740993", response.totals().uploadBytes());
        assertEquals("2", response.totals().downloadBytes());
        assertEquals("9007199254740995", response.totals().transferBytes());
        assertEquals("Test City", response.topUploadCities().data().getFirst().name());
        assertEquals("9007199254740993", response.topUploadCities().data().getFirst().bytes());
        assertEquals("2", response.topDownloadCities().data().getFirst().bytes());
    }

    @SuppressWarnings("unchecked")
    @Test
    void coldRequestsNeverQueryTrafficTables() throws Exception {
        var mapper = mock(BittorrentTrafficMapper.class);
        var service = service(mapper);
        for (int days : java.util.List.of(1, 3, 7)) {
            var response = service.getStatistics(days);
            assertEquals("0", response.totals().transferBytes());
            assertNull(response.generatedAt());
        }
        verifyNoInteractions(mapper);
    }

    @SuppressWarnings("unchecked")
    @Test
    void restartReadsPersistedResultsWithoutTrafficQueries() throws Exception {
        var mapper = mock(BittorrentTrafficMapper.class);
        var feature = mock(BittorrentTrafficFeatureService.class);
        when(feature.isEnabled()).thenReturn(true);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> values =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var json = new ObjectMapper().findAndRegisterModules();
        var snapshots = mock(BittorrentTrafficSnapshotService.class);
        var original = new BittorrentTrafficStatisticsService(mapper, snapshots, mapper, redis, json, feature);
        original.refresh();
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        var payloads = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values, times(3)).set(keys.capture(), payloads.capture());
        for (int index = 0; index < 3; index++) {
            when(values.get(keys.getAllValues().get(index))).thenReturn(payloads.getAllValues().get(index));
        }
        clearInvocations(mapper, snapshots);
        var restarted = new BittorrentTrafficStatisticsService(mapper, snapshots, mapper, redis, json, feature);
        for (int days : java.util.List.of(1, 3, 7)) {
            assertEquals(original.getStatistics(days), restarted.getStatistics(days));
            assertNotNull(restarted.getStatistics(days).generatedAt());
        }
        verifyNoInteractions(mapper, snapshots);
    }

    @SuppressWarnings("unchecked")
    private BittorrentTrafficStatisticsService service(BittorrentTrafficMapper mapper) {
        var feature = mock(BittorrentTrafficFeatureService.class);
        when(feature.isEnabled()).thenReturn(true);
        return new BittorrentTrafficStatisticsService(mapper,
                mock(BittorrentTrafficSnapshotService.class), mapper,
                mock(RedisTemplate.class), new ObjectMapper(), feature);
    }
}
