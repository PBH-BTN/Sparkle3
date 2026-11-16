package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BanSubmissionGuardTest {
    @Test
    void cleanupExpiresEventSummariesAfterThreeDays() {
        var mapper = mock(BanSubmissionGuardMapper.class);
        var guard = new BanSubmissionGuard(mapper, mock(BtnRuntimeConfigService.class));
        var earliestCutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);

        guard.cleanup();

        var latestCutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
        var eventCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).deleteExpiredTargets(any(), eventCutoff.capture(), anyInt());
        assertFalse(eventCutoff.getValue().isBefore(earliestCutoff));
        assertFalse(eventCutoff.getValue().isAfter(latestCutoff));
    }
}
