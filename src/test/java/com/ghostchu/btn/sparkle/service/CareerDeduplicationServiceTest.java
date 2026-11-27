package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.CareerBatchMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CareerDeduplicationServiceTest {
    private final DatabaseConfigService config = mock(DatabaseConfigService.class);
    private final CareerBatchMapper mapper = mock(CareerBatchMapper.class);
    private final CareerDeduplicationService service = new CareerDeduplicationService(config, mapper);
    private static final String KEY = CareerDeduplicationService.RETENTION_DAYS_KEY;

    @Test
    void validatesWholeDaysAndProtectsTheSevenDayReportingWindow() {
        for (String value : List.of("0", "6", "-7", "7.5", "7.0", "3651", "2147483648", "", "x")) {
            assertFalse(CareerDeduplicationService.validRetentionDays(value), value);
        }
        assertFalse(CareerDeduplicationService.validRetentionDays(null));
        assertTrue(CareerDeduplicationService.validRetentionDays("7"));
        assertTrue(CareerDeduplicationService.validRetentionDays("3650"));
    }

    @Test
    void readsUpdatedConfigurationOnEveryCleanupAndHonorsExternalOverrides() {
        when(config.getRawValues(List.of(KEY))).thenReturn(Map.of(), Map.of(KEY, "30"));
        assertEquals(7, service.retentionDays());
        assertEquals(30, service.retentionDays());
        when(config.getExternalOverride(KEY, "30")).thenReturn("14");
        assertEquals(14, service.retentionDays());
    }

    @Test
    void invalidConfigurationStopsCleanupWithoutDeletingAnything() {
        when(config.getRawValues(List.of(KEY))).thenReturn(Map.of(KEY, "3"));
        assertThrows(IllegalStateException.class, service::cleanup);
        verifyNoInteractions(mapper);
    }

    @Test
    void deletesBothTablesInBoundedBatchesWithTheSameCutoff() {
        when(config.getRawValues(List.of(KEY))).thenReturn(Map.of(KEY, "14"));
        when(mapper.deleteExpiredEvents(any(), eq(5000))).thenReturn(5000, 0, 0);
        when(mapper.deleteExpiredCheckpoints(any(), eq(5000))).thenReturn(0, 5000, 1);
        var before = OffsetDateTime.now(ZoneOffset.UTC).minusDays(14);
        service.cleanup();
        var after = OffsetDateTime.now(ZoneOffset.UTC).minusDays(14);
        var cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper, times(3)).deleteExpiredEvents(cutoff.capture(), eq(5000));
        assertFalse(cutoff.getValue().isBefore(before));
        assertFalse(cutoff.getValue().isAfter(after));
        for (var value : cutoff.getAllValues()) assertEquals(cutoff.getValue(), value);
        verify(mapper, times(3)).deleteExpiredCheckpoints(cutoff.getValue(), 5000);
    }

    @Test
    void capsWorkPerRunEvenWithAnExpiryBacklog() {
        when(config.getRawValues(List.of(KEY))).thenReturn(Map.of());
        when(mapper.deleteExpiredEvents(any(), anyInt())).thenReturn(5000);
        when(mapper.deleteExpiredCheckpoints(any(), anyInt())).thenReturn(5000);
        service.cleanup();
        verify(mapper, times(20)).deleteExpiredEvents(any(), eq(5000));
        verify(mapper, times(20)).deleteExpiredCheckpoints(any(), eq(5000));
    }
}
