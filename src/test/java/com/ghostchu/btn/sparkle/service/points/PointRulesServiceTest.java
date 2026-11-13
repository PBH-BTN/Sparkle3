package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointRulesServiceTest {
    @Test
    void readsConfigurableRewardsAndCaps() {
        AnalyseModuleConfigService config = mock(AnalyseModuleConfigService.class);
        when(config.longValue(
                PointRulesService.FIXED_RULES_MODULE_ID,
                "keepalive-points",
                1L
        )).thenReturn(7L);
        when(config.longValue(
                PointRulesService.FIXED_RULES_MODULE_ID,
                "keepalive-daily-cap",
                2_000L
        )).thenReturn(350L);
        when(config.longValue(
                PointRulesService.DYNAMIC_RULES_MODULE_ID,
                "dynamic-hot-peer-top",
                50L
        )).thenReturn(12L);
        when(config.longValue(
                PointRulesService.DYNAMIC_RULES_MODULE_ID,
                "dynamic-torrent-size-gib",
                1L
        )).thenReturn(4L);

        PointRulesService service = new PointRulesService(config);

        assertEquals(7, service.points(PointCategory.KEEPALIVE));
        assertEquals(350, service.dailyCap(PointCategory.KEEPALIVE));
        assertEquals(12, service.dynamicHotPeerTop());
        assertEquals(4, service.dynamicTorrentSizeGib());
        assertNull(service.dailyCap(PointCategory.ACTIVE_APP));
    }
}
