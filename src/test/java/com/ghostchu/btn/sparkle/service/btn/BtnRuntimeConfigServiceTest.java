package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BtnRuntimeConfigServiceTest {
    @Test
    void transactionCanReadAndInvalidateWhileAnotherThreadLoadsTheCache() throws Exception {
        var database = mock(DatabaseConfigService.class);
        var environment = mock(Environment.class);
        String key = BtnConfigCatalog.key("protocol", "minProtocolVersion");
        var loading = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(environment.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(database.getRawValues(any())).thenAnswer(call -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                return Map.of(key, "30");
            }
            loading.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return Map.of(key, "20");
        });
        var service = new BtnRuntimeConfigService(database, environment);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var refresh = executor.submit(service::values);
            try {
                assertTrue(loading.await(5, TimeUnit.SECONDS));
                var transactionalRead = executor.submit(() -> {
                    TransactionSynchronizationManager.setActualTransactionActive(true);
                    try {
                        service.invalidate();
                        return service.values().get(key);
                    } finally {
                        TransactionSynchronizationManager.setActualTransactionActive(false);
                    }
                });
                assertEquals("30", transactionalRead.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertEquals("20", refresh.get(5, TimeUnit.SECONDS).get(key));
            org.mockito.Mockito.doReturn(Map.of(key, "40")).when(database).getRawValues(any());
            assertEquals("40", service.values().get(key));
        }
    }

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
