package com.ghostchu.btn.sparkle.service.impl.denylist;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.service.impl.AbstractAnalyseRuleServiceImpl;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.UnitConverter;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
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
public class AnalyseRuleConcurrentDownloadServiceImpl extends AbstractAnalyseRuleServiceImpl implements IPDenyListRuleProvider {

    @Value("${sparkle.analyse.concurrent-download-analyse.duration}")
    private long duration;
    @Value("${sparkle.analyse.concurrent-download-analyse.threshold-concurrent}")
    private double thresholdConcurrent;
    @Value("${sparkle.analyse.concurrent-download-analyse.threshold-userapps}")
    private double thresholdUserapps;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.concurrent-download-analyse.schedule}")
    @Transactional
    public void analyseOverDownload() {
        log.info("Performing concurrent download analysis with duration: {} ms, thresholdConcurrent: {}, thresholdUserapps: {}", duration, thresholdConcurrent, thresholdUserapps);
        var afterTimestamp = OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS);
        StringBuilder sb = new StringBuilder();

        try (var cursor = this.baseMapper.analyseConcurrentDownload(afterTimestamp)) {
            // 必须在 try 块内完成迭代
            for (var result : cursor) {
                // 边遍历边过滤边输出，不创建中间 List
                if (result.getTorrentCount() >= thresholdConcurrent && result.getUserappsCount() >= thresholdUserapps) {
                    sb.append("# [Sparkle3 并发下载在线分析] 过去给定时间内并发下载计数: ").append(result.getTorrentCount())
                            .append(", 标记人数: ").append(result.getUserappsCount())
                            .append(", BTN 网络发送到此 Peer 的总流量: ").append(UnitConverter.autoUnit(result.getTotalToPeerTraffic()))
                            .append(", BTN 网络从此 Peer 接收的总流量: ").append(UnitConverter.autoUnit(result.getTotalFromPeerTraffic()))
                            .append("\n");
                    IPAddress ipAddress = IPAddressUtil.getIPAddress(result.getPeerIp());
                    sb.append(ipAddress.toNormalizedString()).append("\n");
                }
            }
        } catch (Exception e) {
            log.error("Error processing concurrent download analysis cursor", e);
            return;
        }

        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VALUE.getKey(), sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
        log.info("Concurrent download analysis completed. Result length: {}, version: {}", sb.length(), redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION.getKey()));
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return  redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VALUE.getKey());
    }
}
