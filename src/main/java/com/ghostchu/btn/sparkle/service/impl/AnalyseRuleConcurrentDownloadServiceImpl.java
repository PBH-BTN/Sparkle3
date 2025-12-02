package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseConcurrentDownloadResult;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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
    @Qualifier("genericStringStringTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.concurrent-download-analyse.schedule}")
    public void analyseOverDownload() {
        List<AnalyseConcurrentDownloadResult> resultList = this.baseMapper.analyseConcurrentDownload(new Timestamp(System.currentTimeMillis() - duration));
        resultList.removeIf(result -> result.getTorrentCount() < thresholdConcurrent || result.getUserappsCount() < thresholdUserapps);
        StringBuilder sb = new StringBuilder();
        for (AnalyseConcurrentDownloadResult result : resultList) {
            sb.append("# [Sparkle3 并发下载在线分析] 过去给定时间内并发下载计数: ").append(result.getTorrentCount())
                    .append(", 标记人数: ").append(result.getUserappsCount())
                    .append("\n");
            IPAddress ipAddress = IPAddressUtil.getIPAddress(result.getPeerIp());
            sb.append(ipAddress.toNormalizedString()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VALUE.getKey(), sb.toString());
        //noinspection UnstableApiUsage
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_CONCURRENT_DOWNLOAD_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }
}
