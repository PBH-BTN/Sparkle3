package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseOverDownloadedResult;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.MsgUtil;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyseRuleOverDownloadServiceImpl extends AbstractAnalyseRuleServiceImpl {

    @Value("${sparkle.analyse.overdownload-analyse.duration}")
    private long duration;
    @Value("${sparkle.analyse.overdownload-analyse.threshold-ratio}")
    private double thresholdRatio;
    @Value("${sparkle.analyse.overdownload-analyse.threshold-traffic}")
    private long thresholdTraffic;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.overdownload-analyse.schedule}")
    public void analyseOverDownload() {
        Map<InetAddress, AggregateCrossTorrentMixCalc> aggregateMap = new HashMap<>();
        List<AnalyseOverDownloadedResult> resultList = this.baseMapper.analyseOverDownloaded(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
        for (AnalyseOverDownloadedResult result : resultList) {
            if (result.getTorrentSize() <= 0) continue;
            var inet = InetAddress.ofLiteral(result.getPeerIp());
            var mixCalc = aggregateMap.getOrDefault(inet, new AggregateCrossTorrentMixCalc());
            mixCalc.setTorrentCount(mixCalc.getTorrentCount() + 1);
            mixCalc.setTotalFromPeerTraffic(mixCalc.getTotalFromPeerTraffic() + result.getTotalFromPeerTraffic());
            mixCalc.setTotalToPeerTraffic(mixCalc.getTotalToPeerTraffic() + result.getTotalToPeerTraffic());
            mixCalc.setTotalTorrentSize(mixCalc.getTotalTorrentSize() + result.getTorrentSize());
            aggregateMap.put(inet, mixCalc);
        }
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<InetAddress, AggregateCrossTorrentMixCalc> entry : aggregateMap.entrySet()) {
            InetAddress ip = entry.getKey();
            AggregateCrossTorrentMixCalc calc = entry.getValue();
            if(calc.getOverDownloadRatio() > thresholdRatio){
                if(calc.getPureToPeerTraffic() > thresholdTraffic){
                    sb.append("# [Sparkle3 过量下载在线分析] ")
                            .append(" 过量下载比率: ").append(String.format("%.2f", calc.getOverDownloadRatio() * 100)).append("%")
                            .append(" (100% = 完整下载一次种子大小)")
                            .append(", BTN 网络发送到此 Peer 的流量: ").append(MsgUtil.humanReadableByteCountBin(calc.getTotalToPeerTraffic()))
                            .append(", BTN 网络从此 Peer 接收的流量: ").append(MsgUtil.humanReadableByteCountBin(calc.getTotalFromPeerTraffic()))
                            .append(", 跨种计算数量: ").append(calc.getTorrentCount())
                            .append("\n");
                    sb.append(ip.getHostAddress()).append("\n");
                }
            }
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey(), sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

//    @Scheduled(cron = "${sparkle.analyse.overdownload-analyse.schedule}")
//    public void analyseOverDownload() {
//        List<AnalyseOverDownloadedResult> resultList = this.baseMapper.analyseOverDownloaded(OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
//        var it = resultList.iterator();
//        long totalTorrentSize = 0;
//        long totalToPeerTraffic = 0;
//        long totalFromPeerTraffic = 0;
//        while (it.hasNext()) {
//            var result = it.next();
//            if (result.getTorrentSize() <= 0) continue;
//            double ratio = (result.getTotalToPeerTraffic() - result.getTotalFromPeerTraffic()) / (double) result.getTorrentSize();
//            totalTorrentSize += result.getTorrentSize();
//            totalToPeerTraffic += result.getTotalToPeerTraffic();
//            totalFromPeerTraffic += result.getTotalFromPeerTraffic();
//            if (ratio < threshold) {
//                it.remove();
//            }
//        }
//        StringBuilder sb = new StringBuilder();
//        for (AnalyseOverDownloadedResult result : resultList) {
//            assert result.getTorrentSize() > 0;
//            double ratio = (double) result.getPureToPeerTraffic() / (double) result.getTorrentSize();
//            sb.append("# [Sparkle3 过量下载在线分析]  过量下载比率: ").append(String.format("%.2f", ratio * 100)).append("%")
//                    .append(" (100% = 完整下载一次种子大小), BTN 网络总发送量: ").append(MsgUtil.humanReadableByteCountBin(result.getTotalToPeerTraffic()))
//                    .append(", BTN 网络总接收量: ").append(MsgUtil.humanReadableByteCountBin(result.getTotalFromPeerTraffic()))
//                    .append("\n");
//            IPAddress ipAddress = IPAddressUtil.getIPAddress(result.getPeerIp());
//            sb.append(ipAddress.toNormalizedString()).append("\n");
//        }
//        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey(), sb.toString());
//        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
//    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Data
    public static class AggregateCrossTorrentMixCalc {
        private long torrentCount;
        private long totalToPeerTraffic;
        private long totalFromPeerTraffic;
        private long totalTorrentSize;

        public long getPureToPeerTraffic() {
            return totalToPeerTraffic - totalFromPeerTraffic;
        }

        public double getOverDownloadRatio(){
            if (totalTorrentSize == 0) {
                return 0.0;
            }
            return (double) getPureToPeerTraffic() / (double) totalTorrentSize;
        }

        public double getShareRatio(){
            if (totalFromPeerTraffic == 0) {
                return 0.0;
            }
            return (double) totalToPeerTraffic / (double) totalFromPeerTraffic;
        }
    }
}
