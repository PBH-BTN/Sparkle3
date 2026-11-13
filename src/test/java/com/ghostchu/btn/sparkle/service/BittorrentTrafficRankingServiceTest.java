package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.service.dto.DashboardTrafficRowDto;
import org.junit.jupiter.api.Test;
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
        var service = new BittorrentTrafficRankingService(mapper, feature);
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
        var service = new BittorrentTrafficRankingService(mapper, mock(BittorrentTrafficFeatureService.class));
        service.refresh();
        assertThrows(BusinessException.class, () -> service.getStatistics(7));
        verifyNoInteractions(mapper);
    }
}
