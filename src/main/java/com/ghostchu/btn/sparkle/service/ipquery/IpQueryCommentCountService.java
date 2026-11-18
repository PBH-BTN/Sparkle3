package com.ghostchu.btn.sparkle.service.ipquery;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IpQueryCommentCountService {
    private static final int MAX_BATCH_SIZE = 50;
    private static final RedisScript<Long> LIMIT_REQUESTS = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            return current
            """, Long.class);

    private final IpQueryCommentMapper mapper;
    private final IpQuerySettingsService settings;
    private final BtnRuntimeConfigService config;
    private final RedisTemplate<String, String> redis;

    public IpQueryCommentCountService(
            IpQueryCommentMapper mapper,
            IpQuerySettingsService settings,
            BtnRuntimeConfigService config,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redis
    ) {
        this.mapper = mapper;
        this.settings = settings;
        this.config = config;
        this.redis = redis;
    }

    public Map<String, Long> count(Long applicationId, List<String> ips) throws BusinessException {
        if (ips == null || ips.isEmpty() || ips.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Supply between 1 and 50 IP addresses");
        }
        if (applicationId != null) {
            Long requests = redis.execute(LIMIT_REQUESTS, List.of("sparkle:ip-comment-count:limit:" + applicationId));
            if (requests == null || requests > config.longValue("queryIp", "commentCountRequestsPerMinute")) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Comment count request limit reached");
            }

        }
        Map<String, String> addresses = new LinkedHashMap<>();
        for (String ip : ips) {
            IpScope scope = IpScope.parse(ip);
            if (scope.cidrInput() || scope.address().isMultiple() || ip.indexOf('%') >= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Supply individual IP addresses");
            }
            addresses.put(ip, scope.normalizedAddress());
        }
        Map<String, Long> result = new LinkedHashMap<>();
        if (!settings.get().commentsVisible()) {
            addresses.keySet().forEach(address -> result.put(address, 0L));
            return result;
        }

        long ttl = config.longValue("queryIp", "commentCountCacheSeconds");
        String cachePrefix = "sparkle:ip-comment-count:v1:" + ttl + ":";
        List<String> unique = addresses.values().stream().distinct().toList();
        List<String> cached = redis.opsForValue().multiGet(unique.stream().map(ip -> cachePrefix + ip).toList());
        Map<String, Long> counts = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            String address = unique.get(i);
            String count = cached == null ? null : cached.get(i);
            if (count == null) {
                misses.add(address);
            } else {
                counts.put(address, Long.parseLong(count));
            }
        }
        if (!misses.isEmpty()) {
            for (var item : mapper.countApprovedByAddress(misses)) {
                counts.put(item.address(), item.count());
                redis.opsForValue().set(cachePrefix + item.address(), Long.toString(item.count()), Duration.ofSeconds(ttl));
            }
        }
        addresses.forEach((input, canonical) -> result.put(input, counts.get(canonical)));
        return result;
    }
}
