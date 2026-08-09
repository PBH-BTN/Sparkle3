package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.service.*;
import com.ghostchu.btn.sparkle.service.dto.BanHistoryDto;
import com.ghostchu.btn.sparkle.service.dto.SwarmTrackerDto;
import com.ghostchu.btn.sparkle.util.TimeConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class QueryIpServiceImpl {
    @Value("${sparkle.ping.query-ip.pow-captcha}")
    private boolean powCaptcha;
    @Value("${sparkle.query.query-ip.include-modules}")
    private String queryIpIncludeModules;
    @Value("${sparkle.query.query-ip.bans-counting-duration}")
    private long bansCountingDuration;
    @Value("${sparkle.query.query-ip.swarms-counting-duration}")
    private long swarmsCountingDuration;
    @Value("${sparkle.query.query-ip.heartbeat-query-duration}")
    private long heartbeatDuration;
    @Value("${sparkle.ping.sync-swarm.interval}")
    private long syncSwarmIntervalForConcurrentDownload;
    @Value("${sparkle.ping.sync-swarm.random-initial-delay}")
    private long syncSwarmRandomInitialDelayForConcurrentDownload;
    @Value("${sparkle.query.query-ip.traffic-measure-duration}")
    private long trafficMeasureDuration;
    @Value("${sparkle.query.query-ip.torrents-counting-duration}")
    private long torrentsCountingDuration;
    @Autowired
    private IBanHistoryService banHistoryService;
    @Autowired
    private ITorrentService torrentService;
    @Autowired
    private ISwarmTrackerService swarmTrackerService;
    @Autowired
    private IUserappsHeartbeatService userappsHeartbeatService;
    @Autowired
    private IUserappService userappService;
    @Autowired
    private IUserService userService;

    public @NotNull IpQueryResult queryIp(@NotNull String peerIp) {
        long startTime = System.currentTimeMillis();
        IpQueryResult result = new IpQueryResult();
        result.setColor("#808080");
        result.setLabels(new ArrayList<>());
        var bans = banHistoryService.fetchBanHistory(
                OffsetDateTime.now().minus(bansCountingDuration, ChronoUnit.MILLIS),
                peerIp,
                null,
                List.of(queryIpIncludeModules.split(",")),
                Page.of(1, 1000)
        );
        result.setBans(new IpQueryResult.IpQueryResultBans(bansCountingDuration, bans.getTotal(), bans.getRecords().stream().map(BanHistoryDto::new).toList()));
        log.info("Query BanHistory for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();
        var swarms = swarmTrackerService.fetchSwarmTrackersAfter(
                OffsetDateTime.now().minus(swarmsCountingDuration, ChronoUnit.MILLIS),
                peerIp,
                null,
                Page.of(1, 1000)
        );
        log.info("Query swarm for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();
        var concurrentDownloads = swarmTrackerService.calcPeerConcurrentDownloads(
                OffsetDateTime.now().minus((syncSwarmIntervalForConcurrentDownload + syncSwarmRandomInitialDelayForConcurrentDownload + 120000), ChronoUnit.MILLIS),
                peerIp
        );
        log.info("Query concurrentDownloads for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();
        var concurrentSeeds = swarmTrackerService.calcPeerConcurrentSeeds(
                OffsetDateTime.now().minus((syncSwarmIntervalForConcurrentDownload + syncSwarmRandomInitialDelayForConcurrentDownload + 120000), ChronoUnit.MILLIS),
                peerIp
        );
        result.setSwarms(new IpQueryResult.IpQueryResultSwarms(syncSwarmIntervalForConcurrentDownload, swarms.getTotal(), swarms.getRecords().stream().map(SwarmTrackerDto::new).toList(), concurrentDownloads, concurrentSeeds));
        log.info("Query concurrentSeeds for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();
        long totalToPeerTraffic = 0;
        long totalFromPeerTraffic = 0;
        var trafficMeasureSince = OffsetDateTime.now().minus(trafficMeasureDuration, ChronoUnit.MILLIS);
        var banHistoryTraffic = banHistoryService.sumPeerIpTraffic(trafficMeasureSince, peerIp);
        log.info("Query sumPeerIpTrafficForBanHistory for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();
        var swarmTrackerTraffic = swarmTrackerService.sumPeerIpTraffic(trafficMeasureSince, peerIp);
        log.info("Query sumPeerIpTrafficForSwarmTracker for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        if (banHistoryTraffic != null) {
            totalToPeerTraffic += banHistoryTraffic.getSumToPeerTraffic();
            totalFromPeerTraffic += banHistoryTraffic.getSumFromPeerTraffic();
        }
        if (swarmTrackerTraffic != null) {
            totalToPeerTraffic += swarmTrackerTraffic.getSumToPeerTraffic();
            totalFromPeerTraffic += swarmTrackerTraffic.getSumFromPeerTraffic();
        }
        totalFromPeerTraffic = Math.max(0, totalFromPeerTraffic);
        totalToPeerTraffic = Math.max(0, totalToPeerTraffic);
        var shareRatio =
                totalFromPeerTraffic == 0 ? 0.0 :
                totalToPeerTraffic == 0 ? 999999999.0 :
                (double) totalFromPeerTraffic / totalToPeerTraffic;
        result.setTraffic(new IpQueryResult.IpQueryTraffic(trafficMeasureDuration, totalToPeerTraffic, totalFromPeerTraffic, shareRatio));
        Set<Long> distinctTorrentIds = new HashSet<>();
        OffsetDateTime torrentsCountingSince = OffsetDateTime.now().minus(torrentsCountingDuration, ChronoUnit.MILLIS);
        startTime =  System.currentTimeMillis();
        distinctTorrentIds.addAll(banHistoryService.selectPeerTorrents(torrentsCountingSince, peerIp));
        log.info("Query banHistoryUniqueTorrents for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();
        distinctTorrentIds.addAll(swarmTrackerService.selectPeerIpTorrents(torrentsCountingSince, peerIp));
        log.info("Query swarmUniqueTorrents for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();
        result.setTorrents(new IpQueryResult.IpQueryTorrents(torrentsCountingDuration, distinctTorrentIds.size()));

        var heartbeats = userappsHeartbeatService.fetchIpHeartbeatRecords(peerIp, OffsetDateTime.now().minus(heartbeatDuration, ChronoUnit.MILLIS));
        if (!heartbeats.isEmpty()) {
            var firstResult = heartbeats.getFirst();
            var btnUserApp = userappService.getById(firstResult.getUserappId());
            if (btnUserApp != null) {
                var btnUser = userService.getById(btnUserApp.getOwner());
                if (btnUser != null) {
                    result.getLabels().add("BTN用户");
                }
            }
        }

        log.info("Query heartbeats data for widget costs: {}ms",  System.currentTimeMillis() - startTime);
        startTime =  System.currentTimeMillis();

        return result;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class IpQueryResult {
        @JsonProperty("color")
        private String color;
        @JsonProperty("labels")
        private List<String> labels = Collections.emptyList();
        @JsonProperty("bans")
        private IpQueryResultBans bans;
        @JsonProperty("swarms")
        private IpQueryResultSwarms swarms;
        @JsonProperty("traffic")
        private IpQueryTraffic traffic;
        @JsonProperty("torrents")
        private IpQueryTorrents torrents;


        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryTorrents {
            @JsonProperty("duration")
            private long duration;
            @JsonProperty("count")
            private long count;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryTraffic {
            @JsonProperty("duration")
            private long duration;
            @JsonProperty("to_peer_traffic")
            private long toPeerTraffic;
            @JsonProperty("from_peer_traffic")
            private long fromPeerTraffic;
            @JsonProperty("share_ratio")
            private double shareRatio;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultBans {
            @JsonProperty("duration") // 提供的是最近 duration 时间内的数据，单位是毫秒
            private long duration;
            @JsonProperty("total")
            private long total;
            @JsonProperty("records")
            private List<BanHistoryDto> records;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultSwarms {
            @JsonProperty("duration") // 提供的是最近 duration 时间内的数据，单位是毫秒
            private long duration;
            @JsonProperty("total")
            private long total;
            @JsonProperty("records")
            private List<SwarmTrackerDto> records;
            @JsonProperty("concurrent_download_torrents_count")
            private long concurrentDownloadTorrentsCount;
            @JsonProperty("concurrent_seeding_torrents_count")
            private long concurrentSeedingTorrentsCount;
        }
    }

}
