package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BtnBypassConflictAlertServiceTest {
    @Test
    void matchesNetworksAndRangesForEveryApplicationOnTheAddress() {
        var matches = BtnBypassConflictAlertService.match(Map.of(
                "192.0.2.10", Set.of(1L, 2L),
                "2001:db8::5", Set.of(1L),
                "198.51.100.2", Set.of(3L),
                "203.0.113.2", Set.of(4L)
        ), List.of(
                new RulePreviewEntry("manual", "test", """
                        192.0.2.0/24
                        192.0.2.9 - 192.0.2.10
                        192.0.2.0/24 # duplicate
                        192.0.2.11 - 192.0.2.1
                        198.51.100.2,128,allow
                        203.0.113.2,invalid
                        # 203.0.113.0/24
                        """),
                new RulePreviewEntry("geoip-city-blacklist", null, "2001:db8::/64,1,block")
        ));

        assertEquals(Set.of(1L, 2L), matches.keySet());
        assertEquals(3, matches.get(1L).size());
        assertEquals(2, matches.get(2L).size());
    }

    @Test
    void parsesDistinctPositiveLongUids() {
        assertEquals(List.of(123L, Long.MAX_VALUE), AnalyseModuleConfigService.recipientUids(
                "123， 123, 9223372036854775807"));
        assertEquals(List.of(), AnalyseModuleConfigService.recipientUids(""));
        assertThrows(IllegalArgumentException.class, () -> AnalyseModuleConfigService.recipientUids("0"));
        assertThrows(IllegalArgumentException.class, () -> AnalyseModuleConfigService.recipientUids("9223372036854775808"));
    }
}
