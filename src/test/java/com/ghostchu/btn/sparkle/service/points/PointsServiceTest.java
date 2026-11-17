package com.ghostchu.btn.sparkle.service.points;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.mapper.PointsMapper;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointsServiceTest {
    @Test
    void acquiresPointLocksInOrderBeforeWritingTheLedger() {
        var mapper = mock(PointsMapper.class);
        var config = mock(BtnRuntimeConfigService.class);
        var rules = mock(PointRulesService.class);
        var service = new PointsService(
                mapper, new ObjectMapper(), mock(PointAwardContext.class),
                mock(PointToggleService.class), rules, config
        );
        var now = OffsetDateTime.parse("2026-11-17T00:00:00Z");
        var first = new PointAwardBucket(1, "KEEPALIVE", now);
        first.addAppActivity(12);
        first.addAppActivity(11);
        first.addContribution(PointCategory.KEEPALIVE, 10, 1, 1);
        var second = new PointAwardBucket(2, "KEEPALIVE", now);
        second.addAppActivity(21);
        second.addContribution(PointCategory.KEEPALIVE, 10, 1, 1);
        when(config.text("points", "timeZone")).thenReturn("UTC");
        when(rules.onlineGapMinutes()).thenReturn(15);
        for (long userId : List.of(1L, 2L)) {
            when(mapper.applyDailyCategories(eq(userId), eq(now.toLocalDate()), anyString()))
                    .thenReturn(List.of(Map.of("category", "KEEPALIVE", "awarded", 10L)));
            when(mapper.addBalance(userId, 10)).thenReturn(1);
        }

        service.commitAll(List.of(second, first));

        var order = inOrder(mapper);
        order.verify(mapper).touchAppActivity(11, 1, now, 15);
        order.verify(mapper).touchAppActivity(12, 1, now, 15);
        order.verify(mapper).addBalance(1, 10);
        order.verify(mapper).touchAppActivity(21, 2, now, 15);
        order.verify(mapper).addBalance(2, 10);
        order.verify(mapper).insertLedger(eq(1L), eq(first.getEventId()), eq(now),
                eq("KEEPALIVE"), eq(10L), eq(10L), eq(0L), eq(0L), anyString());
        order.verify(mapper).insertLedger(eq(2L), eq(second.getEventId()), eq(now),
                eq("KEEPALIVE"), eq(10L), eq(10L), eq(0L), eq(0L), anyString());
    }
}
