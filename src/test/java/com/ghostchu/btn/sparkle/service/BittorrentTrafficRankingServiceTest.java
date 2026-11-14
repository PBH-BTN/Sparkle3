package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.service.dto.DashboardTrafficRowDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BittorrentTrafficRankingServiceTest {
    @Test
    void publishesAllWindowsAtomicallyAndRetainsSnapshotOnFailure() throws BusinessException {
        var mapper = mock(BittorrentTrafficMapper.class);
        var feature = mock(BittorrentTrafficFeatureService.class);
        when(feature.isEnabled()).thenReturn(true);
        var row = new DashboardTrafficRowDto();
        row.setCity("Client");
        row.setUploadBytes("9007199254740993");
        row.setDownloadBytes("2");
        when(mapper.selectFeatureTraffic(any(), any(), anyBoolean())).thenReturn(List.of(row));
        var service = service(mapper, feature);
        assertNull(service.getStatistics(7).generatedAt());
        verifyNoInteractions(mapper);
        service.refresh();
        for (int days : List.of(1, 3, 7)) {
            var response = service.getStatistics(days);
            assertEquals("9007199254740993", response.peerIds().getFirst().uploadBytes());
            assertEquals(response.peerIds(), response.clientNames());
            assertNotNull(response.generatedAt());
        }
        var previous = service.getStatistics(7);
        when(mapper.selectFeatureTraffic(any(), any(), anyBoolean())).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class, service::refresh);
        assertSame(previous, service.getStatistics(7));
        assertThrows(BusinessException.class, () -> service.getStatistics(2));
    }

    @Test
    void disabledFeatureDoesNotQueryData() {
        var mapper = mock(BittorrentTrafficMapper.class);
        var service = service(mapper, mock(BittorrentTrafficFeatureService.class));
        service.refresh();
        assertThrows(BusinessException.class, () -> service.getStatistics(7));
        verifyNoInteractions(mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void restartLoadsAllWindowsFromRedisWithoutDatabaseQueries() throws Exception {
        var mapper = mock(BittorrentTrafficMapper.class);
        var feature = mock(BittorrentTrafficFeatureService.class);
        when(feature.isEnabled()).thenReturn(true);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var json = new ObjectMapper().findAndRegisterModules();
        var service = new BittorrentTrafficRankingService(mapper, feature, redis, json);
        service.refresh();
        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), payload.capture());
        when(values.get(key.getValue())).thenReturn(payload.getValue());
        clearInvocations(mapper);
        var restarted = new BittorrentTrafficRankingService(mapper, feature, redis, json);
        for (int days : List.of(1, 3, 7)) {
            assertEquals(service.getStatistics(days), restarted.getStatistics(days));
            assertNotNull(restarted.getStatistics(days).generatedAt());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void initialScheduleWaitsFullRefreshInterval() throws Exception {
        var method = BittorrentTrafficRankingService.class.getMethod("refresh");
        var scheduled = method.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        var monitored = method.getAnnotation(com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask.class);
        assertEquals(43_200_000, scheduled.initialDelay());
        assertEquals(scheduled.initialDelay(), monitored.initialDelayMillis());
    }

    @Test
    @SuppressWarnings("unchecked")
    void brokenOrUnavailableRedisNeverTriggersTrafficQueries() throws Exception {
        var mapper = mock(BittorrentTrafficMapper.class);
        var feature = mock(BittorrentTrafficFeatureService.class);
        when(feature.isEnabled()).thenReturn(true);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("invalid JSON")
                .thenThrow(new IllegalStateException("Redis unavailable"));
        var service = new BittorrentTrafficRankingService(mapper, feature, redis,
                new ObjectMapper().findAndRegisterModules());
        assertNull(service.getStatistics(7).generatedAt());
        assertNull(service.getStatistics(3).generatedAt());
        verifyNoInteractions(mapper);
    }

    @SuppressWarnings("unchecked")
    private BittorrentTrafficRankingService service(BittorrentTrafficMapper mapper,
                                                   BittorrentTrafficFeatureService feature) {
        return new BittorrentTrafficRankingService(mapper, feature, mock(RedisTemplate.class),
                new ObjectMapper().findAndRegisterModules());
    }
}
