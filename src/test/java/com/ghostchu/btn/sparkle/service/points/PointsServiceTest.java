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
    void adjustmentUsesReturnedBalanceForAuditWithoutAReadLock() throws Exception {
        var mapper = mock(PointsMapper.class);
        var service = new PointsService(mapper, new ObjectMapper(), mock(PointAwardContext.class),
                mock(PointToggleService.class), mock(PointRulesService.class), mock(BtnRuntimeConfigService.class));
        when(mapper.adjustBalance(7, -20)).thenReturn(80L);

        org.junit.jupiter.api.Assertions.assertEquals(80, service.adjustByAdmin(7, 1, -20, "Correction"));

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mapper).insertLedger(eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), eq("ADMIN_ADJUSTMENT"), eq(-20L), eq(0L), eq(0L), eq(-20L), payload.capture());
        var audit = new ObjectMapper().readTree(payload.getValue());
        org.junit.jupiter.api.Assertions.assertEquals(100, audit.get("balanceBefore").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(80, audit.get("balanceAfter").asLong());
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).balance(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void refusedAdjustmentDoesNotWriteALedgerEntry() {
        var mapper = mock(PointsMapper.class);
        var service = new PointsService(mapper, new ObjectMapper(), mock(PointAwardContext.class),
                mock(PointToggleService.class), mock(PointRulesService.class), mock(BtnRuntimeConfigService.class));
        when(mapper.balance(7)).thenReturn(10L);
        when(mapper.adjustBalance(7, -20)).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertThrows(com.ghostchu.btn.sparkle.exception.BusinessException.class,
                () -> service.adjustByAdmin(7, 1, -20, "Correction"));
        org.mockito.Mockito.verify(mapper).adjustBalance(7, -20);
        org.mockito.Mockito.verify(mapper).balance(7);
        org.mockito.Mockito.verifyNoMoreInteractions(mapper);
    }

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
