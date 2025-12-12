package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
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
public class AnalyseRain000IdentityServiceImpl extends AbstractAnalyseRuleServiceImpl {

    @Value("${sparkle.analyse.rain000-identity-analyse.duration}")
    private long duration;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.rain000-identity-analyse.schedule}")
    public void analyseRain000Identity() {
        var banhistory = baseMapper.analyseRain000IdentityBanHistory(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        var swarm = baseMapper.analyseRain000IdentitySwarmTracker(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        DualIPv4v6AssociativeTries<Pair<String, String>> tries = new DualIPv4v6AssociativeTries<>();
        banhistory.forEach(r -> tries.put(IPAddressUtil.getIPAddress(r.getPeerIp()), Pair.of(r.getPeerId(), r.getPeerClientName())));
        swarm.forEach(r -> tries.put(IPAddressUtil.getIPAddress(r.getPeerIp()), Pair.of(r.getPeerId(), r.getPeerClientName())));
        mergeIps(tries);
        var map = formatAndIterateIp(tries); StringBuilder sb = new StringBuilder();
        for (Map.Entry<IPAddress, Pair<String, String>> entry : map.entrySet()) {
            sb.append("# [Sparkle3] Rain0.0.0 特征识别: ")
                    .append("采样数据: ").append("PeerId: ").append(entry.getValue().getLeft()).append(" ClientName: ").append(entry.getValue().getRight())
                    .append("\n");
            sb.append(entry.getKey().toNormalizedString()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_RAIN000_IDENTITY_VALUE.getKey(), sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_RAIN000_IDENTITY_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_RAIN000_IDENTITY_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_RAIN000_IDENTITY_VERSION.getKey());
        return Pair.of(version, value);
    }
}
