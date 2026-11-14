package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BtnRuntimeConfigServiceTest {
    @Test
    void routingUsesConfigAndRefreshesAfterChanges() {
        DatabaseConfigService database = mock(DatabaseConfigService.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        when(database.getRawValues(any())).thenReturn(Map.of());
        BtnRuntimeConfigService service = new BtnRuntimeConfigService(database, environment);
        String primary = service.text("rootUrls", "rootUrl");
        String optimized = service.text("rootUrls", "chnRootUrl");
        assertEquals(optimized, service.routeUrl("中国移动", null));
        assertEquals(optimized, service.routeUrl(null, "福建省"));
        assertEquals(primary, service.routeUrl(null, null));
        assertEquals(primary, service.routeUrl("中国电信", "北京市"));
        when(database.getRawValues(any())).thenReturn(Map.of(
                BtnConfigCatalog.key("rootUrls", "chnRouteIsps"), " 电信\n联通 ",
                BtnConfigCatalog.key("rootUrls", "chnRouteProvinces"), ""));
        service.invalidate();
        assertEquals(optimized, service.routeUrl("中国电信", null));
        assertEquals(primary, service.routeUrl("中国移动", "福建省"));
        when(database.getRawValues(any())).thenReturn(Map.of(
                BtnConfigCatalog.key("rootUrls", "autoRouteEnabled"), "false"));
        service.invalidate();
        assertEquals(primary, service.routeUrl("中国移动", "福建省"));
    }

    @Test
    void externalOverridesRemainMoreImportantThanDatabaseValues() {
        DatabaseConfigService databaseConfigService = mock(DatabaseConfigService.class);
        Environment environment = mock(Environment.class);
        String key = BtnConfigCatalog.key("protocol", "minProtocolVersion");
        when(environment.getProperty(anyString(), anyString())).thenAnswer(invocation ->
                invocation.getArgument(1)
        );
        when(environment.getProperty(key, "20")).thenReturn("30");
        when(databaseConfigService.getRawValues(any())).thenReturn(Map.of(key, "20"));
        when(databaseConfigService.hasExternalOverride(key, "20")).thenReturn(true);

        BtnRuntimeConfigService service = new BtnRuntimeConfigService(
                databaseConfigService,
                environment
        );

        assertEquals("30", service.values().get(key));
    }
}
