package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseOverDownloadedResult;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.util.MsgUtil;
import com.google.common.hash.Hashing;
import lombok.Data;
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

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AnalyseRuleOverDownloadServiceImpl extends AbstractAnalyseRuleServiceImpl implements IPDenyListRuleProvider {

    @Value("${sparkle.analyse.overdownload-analyse.duration}")
    private long duration;
    @Value("${sparkle.analyse.overdownload-analyse.threshold-ratio}")
    private double thresholdRatio;
    @Value("${sparkle.analyse.overdownload-analyse.threshold-traffic}")
    private long thresholdTraffic;
    @Value("${sparkle.analyse.overdownload-analyse.use-materialized-view:false}")
    private boolean useMaterializedView;
    @Value("${sparkle.analyse.overdownload-analyse.materialized-view-refresh:true}")
    private boolean materializedViewRefresh;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.overdownload-analyse.schedule}")
    @Transactional(readOnly = true)
    public void analyseOverDownload() {
        long startTime = System.currentTimeMillis();
        log.info("[OverDownload Analysis] Starting over-download analysis for last {} days (mode: {})", 
                duration / (24 * 60 * 60 * 1000), useMaterializedView ? "Materialized View" : "Direct Query");
        
        // Refresh materialized view if enabled and using materialized view
        if (useMaterializedView && materializedViewRefresh) {
            try {
                long refreshStart = System.currentTimeMillis();
                log.info("[OverDownload Analysis] Refreshing materialized view...");
                this.baseMapper.refreshOverDownloadMaterializedView();
                long refreshTime = System.currentTimeMillis() - refreshStart;
                log.info("[OverDownload Analysis] Materialized view refreshed in {} ms", refreshTime);
            } catch (Exception e) {
                log.error("[OverDownload Analysis] Failed to refresh materialized view, falling back to direct query", e);
                // Fall back to direct query on refresh failure
                analyseWithDirectQuery(startTime);
                return;
            }
        }
        
        if (useMaterializedView) {
            analyseWithMaterializedView(startTime);
        } else {
            analyseWithDirectQuery(startTime);
        }
    }
    
    private void analyseWithMaterializedView(long startTime) {
        Map<InetAddress, AggregateCrossTorrentMixCalc> aggregateMap = new HashMap<>();
        AtomicLong processedRows = new AtomicLong(0);
        AtomicLong filteredRows = new AtomicLong(0);

        this.baseMapper.analyseOverDownloadedFromMaterializedViewWithHandler(
            OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS),
            resultContext -> {
                processedRows.incrementAndGet();
                AnalyseOverDownloadedResult result = resultContext.getResultObject();
                if (result.getTorrentSize() <= 0) {
                    filteredRows.incrementAndGet();
                    return;
                }

                var inet = InetAddress.ofLiteral(result.getPeerIp());
                var mixCalc = aggregateMap.getOrDefault(inet, new AggregateCrossTorrentMixCalc());
                mixCalc.setTorrentCount(mixCalc.getTorrentCount() + 1);
                mixCalc.setTotalFromPeerTraffic(mixCalc.getTotalFromPeerTraffic() + result.getTotalFromPeerTraffic());
                mixCalc.setTotalToPeerTraffic(mixCalc.getTotalToPeerTraffic() + result.getTotalToPeerTraffic());
                mixCalc.setTotalTorrentSize(mixCalc.getTotalTorrentSize() + result.getTorrentSize());
                aggregateMap.put(inet, mixCalc);
            }
        );

        long queryEndTime = System.currentTimeMillis();
        log.info("[OverDownload Analysis] Materialized view query completed in {} ms, processed {} rows, filtered {} rows, aggregated to {} unique IPs",
                queryEndTime - startTime, processedRows.get(), filteredRows.get(), aggregateMap.size());

        processResults(aggregateMap, startTime, queryEndTime, processedRows.get());
    }
    
    private void analyseWithDirectQuery(long startTime) {
        Map<InetAddress, AggregateCrossTorrentMixCalc> aggregateMap = new HashMap<>();
        AtomicLong processedRows = new AtomicLong(0);
        AtomicLong filteredRows = new AtomicLong(0);

        // Use ResultHandler to process results one by one, avoiding loading all data into memory
        this.baseMapper.analyseOverDownloadedWithHandler(
            OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS),
            resultContext -> {
                processedRows.incrementAndGet();
                AnalyseOverDownloadedResult result = resultContext.getResultObject();
                if (result.getTorrentSize() <= 0) {
                    filteredRows.incrementAndGet();
                    return;
                }

                var inet = InetAddress.ofLiteral(result.getPeerIp());
                var mixCalc = aggregateMap.getOrDefault(inet, new AggregateCrossTorrentMixCalc());
                mixCalc.setTorrentCount(mixCalc.getTorrentCount() + 1);
                mixCalc.setTotalFromPeerTraffic(mixCalc.getTotalFromPeerTraffic() + result.getTotalFromPeerTraffic());
                mixCalc.setTotalToPeerTraffic(mixCalc.getTotalToPeerTraffic() + result.getTotalToPeerTraffic());
                mixCalc.setTotalTorrentSize(mixCalc.getTotalTorrentSize() + result.getTorrentSize());
                aggregateMap.put(inet, mixCalc);
            }
        );

        long queryEndTime = System.currentTimeMillis();
        log.info("[OverDownload Analysis] Direct query completed in {} ms, processed {} rows, filtered {} rows, aggregated to {} unique IPs",
                queryEndTime - startTime, processedRows.get(), filteredRows.get(), aggregateMap.size());

        processResults(aggregateMap, startTime, queryEndTime, processedRows.get());
    }
    
    private void processResults(Map<InetAddress, AggregateCrossTorrentMixCalc> aggregateMap, long startTime, long queryEndTime, long processedRows) {
        StringBuilder sb = new StringBuilder();
        int violationCount = 0;
        int processedCount = 0;

        // 使用迭代器遍历，处理完立即从 map 中移除，减少内存占用
        var iterator = aggregateMap.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            InetAddress ip = entry.getKey();
            AggregateCrossTorrentMixCalc calc = entry.getValue();
            processedCount++;

            if(calc.getOverDownloadRatio() > thresholdRatio){
                if(calc.getPureToPeerTraffic() > thresholdTraffic){
                    violationCount++;
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

            // 处理完立即移除，释放内存
            iterator.remove();

            // 每处理 1000 条记录记录一次进度
            if (processedCount % 1000 == 0) {
                log.debug("[OverDownload Analysis] Processed {}/{} IPs, found {} violations so far",
                    processedCount, processedCount, violationCount);
            }
        }
        
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey(), sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
        
        long totalTime = System.currentTimeMillis() - startTime;
        long queryTime = queryEndTime - startTime;
        log.info("[OverDownload Analysis] Completed in {} ms (query: {} ms, processing: {} ms), detected {} violations (ratio > {}, traffic > {} bytes)",
                totalTime, queryTime, totalTime - queryTime, violationCount, thresholdRatio, thresholdTraffic);
        if (queryTime > 0) {
            log.info("[OverDownload Analysis] Performance: {} rows/sec", processedRows * 1000.0 / queryTime);
        }
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey());
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
