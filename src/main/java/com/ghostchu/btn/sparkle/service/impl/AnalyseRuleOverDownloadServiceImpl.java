package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseOverDownloadedResult;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.MsgUtil;
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
import java.sql.Timestamp;
import java.util.List;

@Service
public class AnalyseRuleOverDownloadServiceImpl extends AbstractAnalyseRuleServiceImpl {

    @Value("${sparkle.analyse.overdownload-analyse.duration}")
    private long duration;
    @Value("${sparkle.analyse.overdownload-analyse.threshold}")
    private double threshold;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.overdownload-analyse.schedule}")
    public void analyseOverDownload() {
        List<AnalyseOverDownloadedResult> resultList = this.baseMapper.analyseOverDownloaded(new Timestamp(System.currentTimeMillis() - duration));
        var it = resultList.iterator();
        while (it.hasNext()) {
            var result = it.next();
            if (result.getTorrentSize() <= 0) continue;
            double ratio = (double) result.getPureToPeerTraffic() / (double) result.getTorrentSize();
            if (ratio < threshold) {
                {
                    it.remove();
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (AnalyseOverDownloadedResult result : resultList) {
            assert result.getTorrentSize() > 0;
            double ratio = (double) result.getPureToPeerTraffic() / (double) result.getTorrentSize();
            sb.append("# [Sparkle3 过量下载在线分析]  过量下载比率: ").append(String.format("%.2f", ratio * 100)).append("%")
                    .append(" (100% = 完整下载一次种子大小), BTN 网络总发送量: ").append(MsgUtil.humanReadableByteCountBin(result.getTotalToPeerTraffic()))
                    .append(", BTN 网络总接收量: ").append(MsgUtil.humanReadableByteCountBin(result.getTotalFromPeerTraffic()))
                    .append("\n");
            IPAddress ipAddress = IPAddressUtil.getIPAddress(result.getPeerIp());
            sb.append(ipAddress.toNormalizedString()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey(), sb.toString());
        //noinspection UnstableApiUsage
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent(){
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey());
        return Pair.of(version, value);
    }
}
