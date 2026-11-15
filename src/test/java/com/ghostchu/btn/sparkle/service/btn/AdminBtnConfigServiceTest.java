package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.controller.admin.AdminBtnConfigController;
import com.ghostchu.btn.sparkle.controller.admin.dto.BtnConfigModels.BtnConfigFieldView;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminBtnConfigServiceTest {
    @Test
    void exposesIpQueryControlsWhenAnExistingDatabaseHasNoConfigurationRows() {
        var database = mock(DatabaseConfigService.class);
        var runtime = new BtnRuntimeConfigService(database, new MockEnvironment());
        var service = new AdminBtnConfigService(database, runtime, mock(BtnDynamicScheduler.class));
        var controller = new AdminBtnConfigController(service);

        Map<String, BtnConfigFieldView> fields = controller.get().fields().stream()
                .filter(field -> field.section().equals("queryIp"))
                .collect(Collectors.toMap(BtnConfigFieldView::field, Function.identity()));

        assertField(fields.get("powCaptcha"), "boolean", "false", null, null);
        assertField(fields.get("commentCountPowCaptcha"), "boolean", "false", null, null);
        assertField(fields.get("commentCountRequestsPerMinute"), "integer", "12", 1L, 600L);
        assertField(fields.get("commentCountCacheSeconds"), "integer", "300", 1L, 86400L);
        verify(database).insertIfMissing("sparkle.ping.query-ip.comment-count-pow-captcha", "false", "yaml");
        verify(database).insertIfMissing("sparkle.ping.query-ip.comment-count-requests-per-minute", "12", "yaml");
        verify(database).insertIfMissing("sparkle.ping.query-ip.comment-count-cache-seconds", "300", "yaml");
    }

    private static void assertField(BtnConfigFieldView field, String type, String value, Long min, Long max) {
        assertEquals(type, field.valueType(), field.key());
        assertEquals(value, field.value(), field.key());
        assertEquals(min, field.min(), field.key());
        assertEquals(max, field.max(), field.key());
    }
}
