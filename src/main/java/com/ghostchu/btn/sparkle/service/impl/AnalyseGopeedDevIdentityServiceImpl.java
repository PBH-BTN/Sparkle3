package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.google.common.hash.Hashing;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class AnalyseGopeedDevIdentityServiceImpl extends AbstractAnalyseRuleServiceImpl {

    @Value("${sparkle.analyse.gopeeddev-identity-analyse.duration}")
    private long duration;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.gopeeddev-identity-analyse.schedule}")
    public void analyseGopeedDevIdentity() {
        Map<String, Pair<String, String>> randomIdentityMap = new HashMap<>();
        var banhistory = baseMapper.analyseGopeeddevIdentityBanHistory(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        var swarm = baseMapper.analyseGopeeddevIdentitySwarmTracker(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        banhistory.forEach(r -> randomIdentityMap.computeIfAbsent(r.getPeerIp(), (_) -> Pair.of(r.getPeerId(), r.getPeerClientName())));
        swarm.forEach(r -> randomIdentityMap.computeIfAbsent(r.getPeerIp(), (_) -> Pair.of(r.getPeerId(), r.getPeerClientName())));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Pair<String, String>> entry : randomIdentityMap.entrySet()) {
            sb.append("# [Sparkle3] Gopeed dev 特征识别: ")
                    .append("采样数据: ").append("PeerId: ").append(entry.getValue().getLeft()).append(" ClientName: ").append(entry.getValue().getRight())
                    .append("\n");
            sb.append(entry.getKey()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_GOPEEDDEV_IDENTITY_VALUE.getKey(), sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_GOPEEDDEV_IDENTITY_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_GOPEEDDEV_IDENTITY_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_GOPEEDDEV_IDENTITY_VERSION.getKey());
        return Pair.of(version, value);
    }
}
