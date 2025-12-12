package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseIPAndIdentityResult;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AnalyseRandomIdentityServiceImpl extends AbstractAnalyseRuleServiceImpl implements IPDenyListRuleProvider {

    @Value("${sparkle.analyse.random-identity-analyse.duration}")
    private long duration;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.random-identity-analyse.schedule}")
    public void analyseRandomIdentity() {
        var banhistory = baseMapper.analyseRandomIdentityBanHistory(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        var swarm = baseMapper.analyseRandomIdentitySwarmTracker(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        log.info("banhistory entries: {}", banhistory.size());
        log.info("swarm entries: {}", swarm.size());
        DualIPv4v6AssociativeTries<Pair<String, String>> tries = new DualIPv4v6AssociativeTries<>();
        banhistory.forEach(r -> tries.put(IPAddressUtil.getIPAddress(r.getPeerIp()), Pair.of(r.getPeerId(), r.getPeerClientName())));
        swarm.forEach(r -> tries.put(IPAddressUtil.getIPAddress(r.getPeerIp()), Pair.of(r.getPeerId(), r.getPeerClientName())));
        log.info("Before mergeIps: {} entries", tries.size());
        mergeIps(tries);
        log.info("After mergeIps: {} entries", tries.size());
        var map = formatAndIterateIp(tries);
        log.info("formatted to map: {} entries", map.size());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<IPAddress, Pair<String, String>> entry : map.entrySet()) {
            sb.append("# [Sparkle3] 随机特征识别: ")
                    .append("采样数据: ").append("PeerId: ").append(entry.getValue().getLeft()).append(" ClientName: ").append(entry.getValue().getRight())
                    .append("\n");
            sb.append(entry.getKey().toNormalizedString()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VALUE.getKey(),  sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_RANDOM_IDENTITY_VALUE.getKey());
    }
}
