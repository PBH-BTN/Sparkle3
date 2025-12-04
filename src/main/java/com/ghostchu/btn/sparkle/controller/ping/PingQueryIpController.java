package com.ghostchu.btn.sparkle.controller.ping;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.PowCaptchaFailureException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import com.ghostchu.btn.sparkle.service.dto.BanHistoryDto;
import com.ghostchu.btn.sparkle.service.dto.SwarmTrackerDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
public class PingQueryIpController extends BasePingController {
    @Value("${sparkle.ping.query-ip.pow-captcha}")
    private boolean powCaptcha;
    @Value("${sparkle.query.query-ip.include-modules}")
    private String queryIpIncludeModules;
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

    @GetMapping("/ping/queryIp")
    @Cacheable(value = "pingQueryIpCache#600000", key = "#ip + '_' + #torrentIdentifier", unless = "#result == null || !#result.statusCode.is2xxSuccessful()")
    public ResponseEntity<@NotNull IpQueryResult> queryIp(@RequestParam String ip, @RequestParam(required = false) String torrentIdentifier) throws AccessDeniedException, PowCaptchaFailureException, UserApplicationBannedException, UserApplicationNotFoundException {
        if (powCaptcha && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        var userApps = verifyUserApplication();
        Long torrentId = null;
        if (torrentIdentifier != null) {
            var torrent = torrentService.getTorrentByTorrentIdentifier(torrentIdentifier);
            if (torrent != null) {
                torrentId = torrent.getId();
            }
        }
        var peerIp = InetAddress.ofLiteral(ip);

        IpQueryResult result = new IpQueryResult();
        result.setColor("gray");
        var bans = banHistoryService.fetchBanHistory(
                OffsetDateTime.now().minusDays(7),
                peerIp,
                torrentId,
                List.of(queryIpIncludeModules.split(",")),
                Page.of(1, 1000)
        );
        result.setBans(new IpQueryResult.IpQueryResultBans(bans.getTotal(), bans.getRecords().stream().map(BanHistoryDto::new).toList()));
        var swarms = swarmTrackerService.fetchSwarmTrackersAfter(
                OffsetDateTime.now().minusDays(7),
                peerIp,
                torrentId,
                Page.of(1, 1000)
        );
        var concurrentDownloads = swarmTrackerService.calcPeerConcurrentDownloads(
                OffsetDateTime.now().minusSeconds((syncSwarmIntervalForConcurrentDownload + syncSwarmRandomInitialDelayForConcurrentDownload) / 1000 + 120),
                InetAddress.ofLiteral(ip)
        );
        result.setSwarms(new IpQueryResult.IpQueryResultSwarms(swarms.getTotal(), swarms.getRecords().stream().map(SwarmTrackerDto::new).toList(), concurrentDownloads));

        Timestamp trafficMeasureSince = Timestamp.from(OffsetDateTime.now().minusSeconds(trafficMeasureDuration).toInstant());
        var banHistoryTraffic = banHistoryService.sumPeerIpTraffic(trafficMeasureSince, peerIp);
        var swarmTrackerTraffic = swarmTrackerService.sumPeerIpTraffic(trafficMeasureSince, peerIp);
        var totalToPeerTraffic = banHistoryTraffic.getSumToPeerTraffic() + swarmTrackerTraffic.getSumToPeerTraffic();
        var totalFromPeerTraffic = banHistoryTraffic.getSumFromPeerTraffic() + swarmTrackerTraffic.getSumFromPeerTraffic();
        var shareRatio = totalFromPeerTraffic == 0 ? -1 : (double) totalToPeerTraffic / totalFromPeerTraffic;
        result.setTraffic(new IpQueryResult.IpQueryTraffic(trafficMeasureDuration, totalToPeerTraffic, totalFromPeerTraffic, shareRatio));
        Set<Long> distinctTorrentIds = new HashSet<>();
        Timestamp torrentsCountingSince = Timestamp.from(OffsetDateTime.now().minusSeconds(torrentsCountingDuration).toInstant());
        distinctTorrentIds.addAll(banHistoryService.selectPeerTorrents(torrentsCountingSince, peerIp));
        distinctTorrentIds.addAll(swarmTrackerService.selectPeerIpTorrents(torrentsCountingSince, peerIp));
        result.setTorrents(new IpQueryResult.IpQueryTorrents(torrentsCountingDuration, distinctTorrentIds.size()));
        return ResponseEntity.ok(result);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Component
    public static class IpQueryBtnModule implements SparkleBtnAbility {
        @Value("${sparkle.ping.query-ip.endpoint}")
        private String endpoint;
        @Value("${sparkle.ping.query-ip.pow-captcha}")
        @JsonProperty("pow_captcha")
        private boolean powCaptcha;

        @Override
        public String getConfigKey() {
            return "ip_query";
        }
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
            @JsonProperty("total")
            private long total;
            @JsonProperty("records")
            private List<BanHistoryDto> records;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultSwarms {
            @JsonProperty("total")
            private long total;
            @JsonProperty("records")
            private List<SwarmTrackerDto> records;
            @JsonProperty("concurrent_download_torrents_count")
            private long concurrentDownloadTorrentsCount;
        }
    }

}
