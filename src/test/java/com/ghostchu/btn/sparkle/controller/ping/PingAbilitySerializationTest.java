package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PingAbilitySerializationTest {
    @Test
    void serializesHeartbeatAbilityFromHotConfiguration() throws Exception {
        BtnRuntimeConfigService config = mock(BtnRuntimeConfigService.class);
        when(config.text("heartbeat", "endpoint")).thenReturn("{rooturl}/ping/heartbeat");
        when(config.longValue("heartbeat", "interval")).thenReturn(12_345L);
        when(config.longValue("heartbeat", "randomInitialDelay")).thenReturn(6_789L);
        when(config.booleanValue("heartbeat", "powCaptcha")).thenReturn(true);
        when(config.booleanValue("heartbeat", "multiIf")).thenReturn(false);

        PingHeartbeatController.HeartbeatBtnAbility ability =
                new PingHeartbeatController.HeartbeatBtnAbility();
        ReflectionTestUtils.setField(ability, "configService", config);

        String json = JsonMapper.builder().build().writeValueAsString(ability);

        assertTrue(json.contains("\"config_key\":\"heartbeat\""));
        assertTrue(json.contains("\"endpoint\":\"{rooturl}/ping/heartbeat\""));
        assertTrue(json.contains("\"interval\":12345"));
        assertTrue(json.contains("\"random_initial_delay\":6789"));
        assertTrue(json.contains("\"pow_captcha\":true"));
        assertTrue(json.contains("\"multi_if\":false"));
    }
}
