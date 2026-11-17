package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.SupporterRoleMapper;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class SupporterRoleServiceTest {
    @Test
    void reconciliationWritesOneUserAtATime() throws Exception {
        var config = mock(DatabaseConfigService.class);
        var mapper = mock(SupporterRoleMapper.class);
        when(config.getRawValues(anyList())).thenReturn(Map.of("sparkle.memberships.supporter-threshold-cny", "10"));
        when(mapper.findCandidates(0, 200)).thenReturn(List.of(1L, 2L));
        when(mapper.grantEligible(anyList(), any())).thenReturn(1);
        var service = new SupporterRoleService(config, mapper, mock(ExchangeRateService.class), mock(TaskStatusService.class));
        try {
            service.reconcile();
            var order = inOrder(mapper);
            order.verify(mapper).grantEligible(List.of(1L), new BigDecimal("10"));
            order.verify(mapper).grantEligible(List.of(2L), new BigDecimal("10"));
            verify(mapper, never()).grantEligible(List.of(1L, 2L), new BigDecimal("10"));
        } finally {
            service.shutdownEvents();
        }
    }
}
