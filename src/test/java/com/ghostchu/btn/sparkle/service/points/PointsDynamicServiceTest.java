package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.mapper.PointsDynamicMapper;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointsDynamicServiceTest {
    @Test
    void settlesEventsWhenJdbcReturnsTimestampBuckets() {
        PointsDynamicMapper mapper = mock(PointsDynamicMapper.class);
        PointsService pointsService = mock(PointsService.class);
        AnalyseModuleConfigService config = mock(AnalyseModuleConfigService.class);
        PointToggleService toggle = mock(PointToggleService.class);
        PointRulesService rules = mock(PointRulesService.class);
        PointsDynamicService service = new PointsDynamicService(
                mapper,
                pointsService,
                config,
                toggle,
                rules
        );

        OffsetDateTime cursor = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .minusMinutes(1);
        Map<String, Object> row = Map.of(
                "bucket", Timestamp.from(cursor.toInstant()),
                "user_id", 69L,
                "upload_points", 100L,
                "download_points", 0L
        );

        when(config.isEnabled(PointsDynamicService.MODULE_ID)).thenReturn(true);
        when(toggle.isAnyDynamicEnabled()).thenReturn(true);
        when(toggle.isEnabled(PointCategory.DYNAMIC_SEED_UPLOAD)).thenReturn(true);
        when(toggle.isEnabled(PointCategory.DYNAMIC_DOWNLOAD)).thenReturn(false);
        when(toggle.isEnabled(PointCategory.DYNAMIC_HOT_PEER)).thenReturn(false);
        when(rules.points(PointCategory.DYNAMIC_SEED_UPLOAD)).thenReturn(180L);
        when(rules.dynamicSeedUploadMib()).thenReturn(200L);
        when(rules.dynamicTorrentSizeGib()).thenReturn(1L);
        when(mapper.selectLastSuccess(anyString())).thenReturn(cursor);
        when(mapper.selectTrafficEvents(
                any(),
                any(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong()
        )).thenReturn(List.of(row));

        service.refresh();

        ArgumentCaptor<PointAwardBucket> bucket = ArgumentCaptor.forClass(
                PointAwardBucket.class
        );
        verify(pointsService).commit(bucket.capture());
        assertEquals(69L, bucket.getValue().getUserId());
        assertEquals(cursor.plusHours(1), bucket.getValue().getOccurredAt());
        assertEquals(1, bucket.getValue().getClaims().size());
        PointAwardBucket.Claim claim = bucket.getValue().getClaims().getFirst();
        assertEquals(PointCategory.DYNAMIC_SEED_UPLOAD, claim.category());
        assertEquals(100L, claim.points());
    }
}
