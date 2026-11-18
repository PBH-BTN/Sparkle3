package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.entity.*;
import com.ghostchu.btn.sparkle.service.impl.allowlist.AnalyseBtnBypassServiceImpl;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BtnBypassAllowlistPrivacyTest {
    @Test
    @SuppressWarnings("unchecked")
    void generatedCommentsNeverIncludeEmail() {
        var service=new AnalyseBtnBypassServiceImpl();
        var apps=mock(IUserappService.class);
        var users=mock(IUserService.class);
        var heartbeat=mock(IUserappsHeartbeatService.class);
        var config=mock(AnalyseModuleConfigService.class);
        RedisTemplate<String,String> redis=mock(RedisTemplate.class);
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ReflectionTestUtils.setField(service,"redisTemplate",redis);
        ReflectionTestUtils.setField(service,"userappService",apps);
        ReflectionTestUtils.setField(service,"iUserService",users);
        ReflectionTestUtils.setField(service,"iUserappsHeartbeatService",heartbeat);
        ReflectionTestUtils.setField(service,"moduleConfigService",config);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        when(config.isEnabled("btnbypass-userapps")).thenReturn(true);
        when(apps.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(new Userapp().setId(1L).setOwner(2L).setComment("Example app")));
        when(users.getById(2L)).thenReturn(new User().setId(2L).setNickname("Example").setEmail("private@example.invalid"));
        when(heartbeat.fetchHeartBeatsByUserAppIdInTimeRange(eq(1L),any(),any())).thenReturn(List.of(
                new UserappsHeartbeat().setIp(InetAddress.ofLiteral("192.0.2.10")).setLastSeenAt(OffsetDateTime.now())));
        service.analyseBtnBypass();
        var snapshot = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(values).multiSet(snapshot.capture());
        String content = (String) snapshot.getValue().get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VALUE.getKey());
        assertFalse(content.contains("Email="));
        assertFalse(content.contains("private@example.invalid"));
        assertTrue(content.contains("(UID=2)"));
        assertTrue(content.contains("192.0.2.10"));
        assertEquals("{\"192.0.2.10\":[1]}",
                snapshot.getValue().get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_OWNERS.getKey()));
    }
}
