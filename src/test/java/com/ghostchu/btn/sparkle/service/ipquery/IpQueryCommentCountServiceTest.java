package com.ghostchu.btn.sparkle.service.ipquery;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper.AddressCommentCount;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IpQueryCommentCountServiceTest {
    private final IpQueryCommentMapper mapper = mock(IpQueryCommentMapper.class);
    private final IpQuerySettingsService settings = mock(IpQuerySettingsService.class);
    private final BtnRuntimeConfigService config = mock(BtnRuntimeConfigService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final IpQueryCommentCountService service = new IpQueryCommentCountService(mapper, settings, config, redis);

    @Test
    void enforcesHardBatchLimitBeforeRedis() {
        assertThrows(BusinessException.class, () -> service.count(1L, Collections.nCopies(51, "192.0.2.1")));
        verifyNoInteractions(redis, mapper);
    }

    @Test
    void rateLimitStopsQueryBeforeCacheOrDatabase() {
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(13L);
        when(config.longValue("queryIp", "commentCountRequestsPerMinute")).thenReturn(12L);

        assertThrows(BusinessException.class, () -> service.count(1L, List.of("192.0.2.1")));
        verifyNoInteractions(values, mapper);
    }

    @Test
    void cachesZeroCountsWithConfiguredTtlAndQueriesOnlyMisses() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        when(config.longValue("queryIp", "commentCountRequestsPerMinute")).thenReturn(12L);
        when(config.longValue("queryIp", "commentCountCacheSeconds")).thenReturn(40L);
        var active = mock(IpQuerySettingsService.Settings.class);
        when(active.commentsVisible()).thenReturn(true);
        when(settings.get()).thenReturn(active);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyList())).thenReturn(Arrays.asList("3", null));
        when(mapper.countApprovedByAddress(List.of("192.0.2.2")))
                .thenReturn(List.of(new AddressCommentCount("192.0.2.2", 0)));

        assertEquals(Map.of("192.0.2.1", 3L, "192.0.2.2", 0L),
                service.count(1L, List.of("192.0.2.1", "192.0.2.2", "192.0.2.2")));
        verify(mapper).countApprovedByAddress(List.of("192.0.2.2"));
        verify(values).set("sparkle:ip-comment-count:v1:40:192.0.2.2", "0", Duration.ofSeconds(40));
    }

    @Test
    void bindsOneSqlValuePerAddressAndUsesNetworkContainment() {
        Configuration configuration = new Configuration();
        configuration.addMapper(IpQueryCommentMapper.class);
        var statement = configuration.getMappedStatement(
                IpQueryCommentMapper.class.getName() + ".countApprovedByAddress");
        var sql = statement.getBoundSql(Map.of("addresses", List.of("192.0.2.1", "2001:db8::1")));

        assertEquals(2, sql.getParameterMappings().size());
        assertTrue(sql.getSql().contains("c.network >>= input.address::inet"));
    }
}
