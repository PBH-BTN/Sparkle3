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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class AnalyseRandomIdentityServiceImpl extends AbstractAnalyseRuleServiceImpl implements IPDenyListRuleProvider {

    @Value("${sparkle.analyse.random-identity-analyse.duration}")
    private long duration;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.random-identity-analyse.schedule}")
    @Transactional(readOnly = true)
    public void analyseRandomIdentity() {
        var afterTimestamp = OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS);
        DualIPv4v6AssociativeTries<Pair<String, String>> tries = new DualIPv4v6AssociativeTries<>();

        try (var banhistory = baseMapper.analyseRandomIdentityBanHistory(afterTimestamp);
             var swarm = baseMapper.analyseRandomIdentitySwarmTracker(afterTimestamp)) {
            // 必须在 try 块内完成迭代
            for (var r : banhistory) {
                tries.put(IPAddressUtil.getIPAddress(r.getPeerIp()), Pair.of(r.getPeerId(), r.getPeerClientName()));
            }
            for (var r : swarm) {
                tries.put(IPAddressUtil.getIPAddress(r.getPeerIp()), Pair.of(r.getPeerId(), r.getPeerClientName()));
            }
        } catch (Exception e) {
            log.error("Error processing random identity analysis cursors", e);
            return;
        }

        mergeIps(tries);

        // 边遍历边输出，不创建中间 Map
        StringBuilder sb = new StringBuilder();
        tries.nodeIterator(false).forEachRemaining(node -> {
            IPAddress ip = node.getKey();
            Pair<String, String> value = node.getValue();

            sb.append("# [Sparkle3] 随机特征识别: ")
                    .append("采样数据: ").append("PeerId: ").append(value.getLeft()).append(" ClientName: ").append(value.getRight())
                    .append("\n");

            // 格式化输出 IP 地址
            IPAddress outputAddr = ip;
            if (outputAddr.getPrefixLength() != null) {
                if ((outputAddr.isIPv4() && outputAddr.getPrefixLength() == 32) || (outputAddr.isIPv6() && outputAddr.getPrefixLength() == 128)) {
                    outputAddr = outputAddr.withoutPrefixLength();
                }
            }
            sb.append(outputAddr.toNormalizedString()).append("\n");
        });

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
