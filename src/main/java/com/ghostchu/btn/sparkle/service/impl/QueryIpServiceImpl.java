package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.service.*;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.dto.BanHistoryDto;
import com.ghostchu.btn.sparkle.service.dto.SwarmTrackerDto;
import com.ghostchu.btn.sparkle.service.ipquery.IpScope;
import com.ghostchu.btn.sparkle.util.TimeConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class QueryIpServiceImpl {
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
    @Autowired
    private BtnRuntimeConfigService btnConfigService;

    public @NotNull IpQueryResult queryIp(@NotNull String peerIp) {
        try {
            peerIp = IpScope.parse(peerIp).canonicalCidr();
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    exception.getMessage() == null
                            ? "Invalid IP address or CIDR"
                            : exception.getMessage(),
                    exception
            );
        }
        String queryIpIncludeModules = btnConfigService.text(
                "queryIpStatistics",
                "includeModules"
        );
        long bansCountingDuration = btnConfigService.longValue(
                "queryIpStatistics",
                "bansCountingDuration"
        );
        long swarmsCountingDuration = btnConfigService.longValue(
                "queryIpStatistics",
                "swarmsCountingDuration"
        );
        long heartbeatDuration = btnConfigService.longValue(
                "queryIpStatistics",
                "heartbeatQueryDuration"
        );
        long trafficMeasureDuration = btnConfigService.longValue(
                "queryIpStatistics",
                "trafficMeasureDuration"
        );
        long torrentsCountingDuration = btnConfigService.longValue(
                "queryIpStatistics",
                "torrentsCountingDuration"
        );
        long syncSwarmInterval = btnConfigService.longValue("syncSwarm", "interval");
        long syncSwarmRandomInitialDelay = btnConfigService.longValue(
                "syncSwarm",
                "randomInitialDelay"
        );
        long concurrentDownloadWindow = syncSwarmInterval
                + syncSwarmRandomInitialDelay
                + 120_000;
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
        var swarms = swarmTrackerService.fetchSwarmTrackersAfter(
                OffsetDateTime.now().minus(swarmsCountingDuration, ChronoUnit.MILLIS),
                peerIp,
                null,
                Page.of(1, 1000)
        );
        var concurrentDownloads = swarmTrackerService.calcPeerConcurrentDownloads(
                OffsetDateTime.now().minus(concurrentDownloadWindow, ChronoUnit.MILLIS),
                peerIp
        );
        var concurrentSeeds = swarmTrackerService.calcPeerConcurrentSeeds(
                OffsetDateTime.now().minus(concurrentDownloadWindow, ChronoUnit.MILLIS),
                peerIp
        );
        result.setSwarms(new IpQueryResult.IpQueryResultSwarms(
                syncSwarmInterval,
                swarms.getTotal(),
                swarms.getRecords().stream().map(SwarmTrackerDto::new).toList(),
                concurrentDownloads,
                concurrentSeeds
        ));
        long totalToPeerTraffic = 0;
        long totalFromPeerTraffic = 0;
        var trafficMeasureSince = OffsetDateTime.now().minus(trafficMeasureDuration, ChronoUnit.MILLIS);
        var banHistoryTraffic = banHistoryService.sumPeerIpTraffic(trafficMeasureSince, peerIp);
        var swarmTrackerTraffic = swarmTrackerService.sumPeerIpTraffic(trafficMeasureSince, peerIp);
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
        distinctTorrentIds.addAll(banHistoryService.selectPeerTorrents(torrentsCountingSince, peerIp));
        distinctTorrentIds.addAll(swarmTrackerService.selectPeerIpTorrents(torrentsCountingSince, peerIp));
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
