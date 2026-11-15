package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminConfigServiceTest {
    private static final String NETWORK_TRAFFIC_KEY = "sparkle.homepage.network-traffic-enabled";
    private final DatabaseConfigService databaseConfig = mock(DatabaseConfigService.class);
    private final AdminConfigService config = new AdminConfigService(
            databaseConfig, 1, 10, false, true, true, 60, 7, true, "{{title}}", "{{content}}"
    );

    @Test
    void homepageNetworkTrafficDefaultsToEnabledAndCanBeDisabled() throws BusinessException {
        assertTrue(config.homepageNetworkTrafficEnabled());
        config.update(new RuntimeConfigView(1, 10, false, true, true, 60, 7, false, "{{title}}", "{{content}}"), 1L);
        assertFalse(config.homepageNetworkTrafficEnabled());
        config.update(new RuntimeConfigView(1, 10, false, true, true, 60, 7, null, "{{title}}", "{{content}}"), 1L);
        assertFalse(config.homepageNetworkTrafficEnabled());
        verify(databaseConfig, times(1)).update(NETWORK_TRAFFIC_KEY, "false", 1L);
    }

    @Test
    void externalConfigurationCanOverrideTheStoredHomepageSetting() {
        when(databaseConfig.getRawValues(anyList())).thenReturn(Map.of(NETWORK_TRAFFIC_KEY, "false"));
        when(databaseConfig.hasExternalOverride(NETWORK_TRAFFIC_KEY, "false")).thenReturn(true);
        assertFalse(config.get().homepageNetworkTrafficEnabled());
        assertTrue(config.homepageNetworkTrafficEnabled());
    }
}
