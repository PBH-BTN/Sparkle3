package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseConcurrentDownloadResult;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.UnitConverter;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
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
import java.util.List;

@Service
public class AnalyseRuleConcurrentDownloadServiceImpl extends AbstractAnalyseRuleServiceImpl {

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
    public void analyseOverDownload() {
        List<AnalyseConcurrentDownloadResult> resultList = this.baseMapper.analyseConcurrentDownload(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        resultList.removeIf(result -> result.getTorrentCount() < thresholdConcurrent || result.getUserappsCount() < thresholdUserapps);
        StringBuilder sb = new StringBuilder();
        for (AnalyseConcurrentDownloadResult result : resultList) {
            sb.append("# [Sparkle3 并发下载在线分析] 过去给定时间内并发下载计数: ").append(result.getTorrentCount())
                    .append(", 标记人数: ").append(result.getUserappsCount())
                    .append(", BTN 网络发送到此 Peer 的总流量: ").append(UnitConverter.autoUnit(result.getTotalToPeerTraffic()))
                    .append(", BTN 网络从此 Peer 接收的总流量: ").append(UnitConverter.autoUnit(result.getTotalFromPeerTraffic()))
                    .append("\n");
            IPAddress ipAddress = IPAddressUtil.getIPAddress(result.getPeerIp());
            sb.append(ipAddress.toNormalizedString()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VALUE.getKey(), sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION.getKey());
        return Pair.of(version, value);
    }
}
