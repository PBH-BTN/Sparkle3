package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.entity.User;
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
    private final User signedInUser = new User().setRole("user");
    private final AdminConfigService config = new AdminConfigService(
            databaseConfig, 1, 10, false, true, true, 60, 7, true, false, true, "{{title}}", "{{content}}"
    );

    @Test
    void homepageNetworkTrafficDefaultsToEnabledAndCanBeDisabled() throws BusinessException {
        assertTrue(config.homepageTrafficAccess(signedInUser).traffic());
        config.update(new RuntimeConfigView(1, 10, false, true, true, 60, 7, false, null, null, null, null, null, null, "{{title}}", "{{content}}"), 1L);
        assertFalse(config.homepageTrafficAccess(signedInUser).traffic());
        config.update(new RuntimeConfigView(1, 10, false, true, true, 60, 7, null, null, null, null, null, null, null, "{{title}}", "{{content}}"), 1L);
        assertFalse(config.homepageTrafficAccess(signedInUser).traffic());
        verify(databaseConfig, times(1)).update(NETWORK_TRAFFIC_KEY, "false", 1L);
    }

    @Test
    void enforcesIndependentAudiencesForCurrentRoles() throws BusinessException {
        var user = new User().setRole("user");
        var moderator = new User().setRole("moderator");
        assertTrue(config.homepageTrafficAccess(user).traffic());
        assertFalse(config.homepageTrafficAccess(user).totalLoss());
        assertTrue(config.homepageTrafficAccess(moderator).totalLoss());
        assertTrue(config.homepageTrafficAccess(new User().setRole("admin")).totalLoss());
        config.update(new RuntimeConfigView(1, 10, false, true, true, 60, 7, true, true, false, true, false, true, false, "{{title}}", "{{content}}"), 1L);
        var access = config.homepageTrafficAccess(user);
        assertFalse(access.reported());
        assertTrue(access.confirmed());
        assertFalse(access.activity());
        assertTrue(access.averageLoss());
        assertTrue(config.homepageTrafficAccess(user).totalLoss());
        assertTrue(config.homepageTrafficAccess(moderator).traffic());
        assertFalse(config.homepageTrafficAccess(null).totalLoss());
        verify(databaseConfig).insertIfMissing("sparkle.homepage.reported-staff-only", "true", "database");
        verify(databaseConfig).update("sparkle.homepage.confirmed-staff-only", "false", 1L);
    }

    @Test
    void chartsInheritLegacyAudienceUntilConfiguredIndividually() {
        when(databaseConfig.getRawValues(anyList())).thenReturn(Map.of(
                "sparkle.homepage.network-traffic-staff-only", "true",
                "sparkle.homepage.confirmed-staff-only", "false"));
        var access = config.homepageTrafficAccess(signedInUser);
        assertFalse(access.reported());
        assertTrue(access.confirmed());
        assertFalse(access.activity());
        assertFalse(access.averageLoss());
    }

    @Test
    void externalConfigurationCanOverrideTheStoredHomepageSetting() {
        when(databaseConfig.getRawValues(anyList())).thenReturn(Map.of(NETWORK_TRAFFIC_KEY, "false"));
        when(databaseConfig.hasExternalOverride(NETWORK_TRAFFIC_KEY, "false")).thenReturn(true);
        assertFalse(config.get().homepageNetworkTrafficEnabled());
        assertTrue(config.homepageTrafficAccess(signedInUser).traffic());
    }
}
