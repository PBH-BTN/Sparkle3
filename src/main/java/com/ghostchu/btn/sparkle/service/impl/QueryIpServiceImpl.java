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
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;

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
    ;

    public @NotNull IpQueryResult queryIp(@NotNull InetAddress peerIp) {
        IpQueryResult result = new IpQueryResult();
        result.setColor("#808080");
        result.setLabels(new ArrayList<>());
        var bans = banHistoryService.fetchBanHistory(
                OffsetDateTime.now().minusSeconds(bansCountingDuration / 1000),
                peerIp,
                null,
                List.of(queryIpIncludeModules.split(",")),
                Page.of(1, 1000)
        );
        result.setBans(new IpQueryResult.IpQueryResultBans(bansCountingDuration, bans.getTotal(), bans.getRecords().stream().map(BanHistoryDto::new).toList()));
        var swarms = swarmTrackerService.fetchSwarmTrackersAfter(
                OffsetDateTime.now().minusSeconds(swarmsCountingDuration / 1000),
                peerIp,
                null,
                Page.of(1, 1000)
        );
        var concurrentDownloads = swarmTrackerService.calcPeerConcurrentDownloads(
                OffsetDateTime.now().minusSeconds((syncSwarmIntervalForConcurrentDownload + syncSwarmRandomInitialDelayForConcurrentDownload) / 1000 + 120),
                peerIp
        );
        var concurrentSeeds = swarmTrackerService.calcPeerConcurrentSeeds(
                OffsetDateTime.now().minusSeconds((syncSwarmIntervalForConcurrentDownload + syncSwarmRandomInitialDelayForConcurrentDownload) / 1000 + 120),
                peerIp
        );
        result.setSwarms(new IpQueryResult.IpQueryResultSwarms(syncSwarmIntervalForConcurrentDownload, swarms.getTotal(), swarms.getRecords().stream().map(SwarmTrackerDto::new).toList(), concurrentDownloads));

        long totalToPeerTraffic = 0;
        long totalFromPeerTraffic = 0;
        Timestamp trafficMeasureSince = Timestamp.from(OffsetDateTime.now().minusSeconds(trafficMeasureDuration).toInstant());
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
        var shareRatio = totalFromPeerTraffic == 0 ? -1 : (double) totalToPeerTraffic / totalFromPeerTraffic;
        result.setTraffic(new IpQueryResult.IpQueryTraffic(trafficMeasureDuration, totalToPeerTraffic, totalFromPeerTraffic, shareRatio));
        Set<Long> distinctTorrentIds = new HashSet<>();
        Timestamp torrentsCountingSince = Timestamp.from(OffsetDateTime.now().minusSeconds(torrentsCountingDuration).toInstant());
        distinctTorrentIds.addAll(banHistoryService.selectPeerTorrents(torrentsCountingSince, peerIp));
        distinctTorrentIds.addAll(swarmTrackerService.selectPeerIpTorrents(torrentsCountingSince, peerIp));
        result.setTorrents(new IpQueryResult.IpQueryTorrents(torrentsCountingDuration, distinctTorrentIds.size()));

        var heartbeats = userappsHeartbeatService.fetchIpHeartbeatRecords(peerIp, Timestamp.from(OffsetDateTime.now().minusSeconds(heartbeatDuration).toInstant()));
        if (!heartbeats.isEmpty()) {
            var firstResult = heartbeats.getFirst();
            var btnUserApp = userappService.getById(firstResult.getUserappId());
            if (btnUserApp != null) {
                var btnUser = userService.getById(btnUserApp.getOwner());
                if (btnUser != null) {
                    result.getLabels().add("[BTN用户] " + btnUser.getNickname() + " (UID:" + btnUser.getId() + ") / " + TimeConverter.INSTANCE.formatTime(firstResult.getLastSeenAt(), "UTC") +" (UTC)");
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
