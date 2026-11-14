package com.ghostchu.btn.sparkle.service;

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
        when(config.isEnabled("btnbypass-userapps")).thenReturn(true);
        when(apps.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(new Userapp().setId(1L).setOwner(2L).setComment("Example app")));
        when(users.getById(2L)).thenReturn(new User().setId(2L).setNickname("Example").setEmail("private@example.invalid"));
        when(heartbeat.fetchHeartBeatsByUserAppIdInTimeRange(eq(1L),any(),any())).thenReturn(List.of(
                new UserappsHeartbeat().setIp(InetAddress.ofLiteral("192.0.2.10")).setLastSeenAt(OffsetDateTime.now())));
        service.analyseBtnBypass();
        var content=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VALUE.getKey()),content.capture());
        assertFalse(content.getValue().contains("Email="));
        assertFalse(content.getValue().contains("private@example.invalid"));
        assertTrue(content.getValue().contains("(UID=2)"));
        assertTrue(content.getValue().contains("192.0.2.10"));
    }
}
