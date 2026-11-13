package com.ghostchu.btn.sparkle.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UserappsHeartbeatServiceImplTest {
    @Test
    @SuppressWarnings("unchecked")
    void stoppedQueueFlushDoesNotAccessRedis() {
        UserappsHeartbeatServiceImpl service = new UserappsHeartbeatServiceImpl();
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);

        service.stopQueueFlush();
        service.flushQueuedHeartbeats();

        verifyNoInteractions(redisTemplate);
    }
}
