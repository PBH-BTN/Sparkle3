package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
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
import java.util.Map;

@Service
@Slf4j
public class AnalyseDatacenterHighRiskServiceImpl extends AbstractAnalyseRuleServiceImpl implements IPDenyListRuleProvider {

    @Value("${sparkle.analyse.datacenter-highrisk-analyse.duration}")
    private long duration;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.datacenter-highrisk-analyse.schedule}")
    public void analyseDatacenterHighRisk() {
        var afterTimestamp = OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS);
        var banhistory = baseMapper.analyseDatacenterHighRiskBanHistory(afterTimestamp);
        var swarmTracker = baseMapper.analyseDatacenterHighRiskSwarmTracker(afterTimestamp);
        DualIPv4v6AssociativeTries<String> tries = new DualIPv4v6AssociativeTries<>();
        banhistory.forEach(r -> tries.put(IPAddressUtil.getIPAddress(r), "Datacenter"));
        swarmTracker.forEach(r -> tries.put(IPAddressUtil.getIPAddress(r), "Datacenter"));
        mergeIps(tries);
        var map = formatAndIterateIp(tries);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<IPAddress, String> entry : map.entrySet()) {
            sb.append("# [Sparkle3] 数据中心高风险特征识别").append("\n");
            sb.append(entry.getKey().toNormalizedString()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VALUE.getKey(),  sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VALUE.getKey());
    }
}
