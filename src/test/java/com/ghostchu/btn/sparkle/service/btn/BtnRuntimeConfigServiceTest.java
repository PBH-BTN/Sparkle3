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
