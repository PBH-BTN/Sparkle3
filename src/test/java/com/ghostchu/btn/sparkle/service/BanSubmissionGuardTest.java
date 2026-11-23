package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BanSubmissionGuardTest {
    private OffsetDateTime expectedCutoff() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).minusDays(6);
    }

    @Test
    void cleanupExpiresTargetsOutsideTheSevenUtcDayWindow() {
        var mapper = mock(BanSubmissionGuardMapper.class);
        var guard = new BanSubmissionGuard(mapper, mock(BtnRuntimeConfigService.class));
        var earliestCutoff = expectedCutoff();

        guard.cleanup();

        var latestCutoff = expectedCutoff();
        var eventCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).deleteExpiredTargets(eventCutoff.capture(), anyInt());
        assertFalse(eventCutoff.getValue().isBefore(earliestCutoff));
        assertFalse(eventCutoff.getValue().isAfter(latestCutoff));
    }
}
