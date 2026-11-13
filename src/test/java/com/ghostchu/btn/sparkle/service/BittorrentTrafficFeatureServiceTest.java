package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BittorrentTrafficFeatureServiceTest {
    @Test
    void cachesConfigurationAndReloadsAfterInvalidation() {
        DatabaseConfigService configService = mock(DatabaseConfigService.class);
        when(configService.getRawValues(List.of(BittorrentTrafficFeatureService.ENABLED_CONFIG_KEY)))
                .thenReturn(Map.of(BittorrentTrafficFeatureService.ENABLED_CONFIG_KEY, "false"))
                .thenReturn(Map.of(BittorrentTrafficFeatureService.ENABLED_CONFIG_KEY, "true"));
        BittorrentTrafficFeatureService featureService =
                new BittorrentTrafficFeatureService(configService);

        assertFalse(featureService.isEnabled());
        assertFalse(featureService.isEnabled());

        featureService.invalidate();

        assertTrue(featureService.isEnabled());
        verify(configService, times(2)).getRawValues(
                List.of(BittorrentTrafficFeatureService.ENABLED_CONFIG_KEY)
        );
    }
}
