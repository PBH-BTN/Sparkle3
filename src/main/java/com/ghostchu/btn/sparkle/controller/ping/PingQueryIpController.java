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
import com.ghostchu.btn.sparkle.service.impl.BanHistoryServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.SwarmTrackerServiceImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@RestController
public class PingQueryIpController extends BasePingController {
    @Value("${sparkle.ping.query-ip.pow-captcha}")
    private boolean powCaptcha;
    @Value("${sparkle.query.query-ip.include-modules}")
    private String queryIpIncludeModules;
    @Value("${sparkle.ping.sync-swarm.interval}")
    private long syncSwarmInterval;
    @Value("${sparkle.ping.sync-swarm.random-initial-delay}")
    private long syncSwarmRandomInitialDelay;
    @Autowired
    private IBanHistoryService banHistoryService;
    @Autowired
    private ITorrentService torrentService;
    @Autowired
    private ISwarmTrackerService swarmTrackerService;

    @GetMapping("/ping/queryIp")
    public ResponseEntity<?> queryIp(@RequestParam String ip, @RequestParam(required = false) String torrentIdentifier) throws AccessDeniedException, PowCaptchaFailureException, UserApplicationBannedException, UserApplicationNotFoundException {
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
        IpQueryResult result = new IpQueryResult();
        result.setColor("gray");
        var bans = banHistoryService.fetchBanHistory(
                OffsetDateTime.now().minusDays(7),
                InetAddress.ofLiteral(ip),
                torrentId,
                List.of(queryIpIncludeModules.split(",")),
                Page.of(1, 1000)
        );
        result.setBans(new IpQueryResult.IpQueryResultBans(bans.getTotal(), bans.getRecords().stream().map(BanHistoryServiceImpl.BanHistoryDto::new).toList()));
        var swarms = swarmTrackerService.fetchSwarmTrackersAfter(
                OffsetDateTime.now().minusDays(7),
                InetAddress.ofLiteral(ip),
                torrentId,
                Page.of(1, 1000)
        );
        var concurrentDownloads = swarmTrackerService.calcPeerConcurrentDownloads(
                OffsetDateTime.now().minusSeconds((syncSwarmInterval + syncSwarmRandomInitialDelay) / 1000 + 120),
                InetAddress.ofLiteral(ip)
        );
        result.setSwarms(new IpQueryResult.IpQueryResultSwarms(swarms.getTotal(), swarms.getRecords().stream().map(SwarmTrackerServiceImpl.SwarmTrackerDto::new).toList(), concurrentDownloads));
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

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultBans {
            @JsonProperty("total")
            private long total;
            @JsonProperty("records")
            private List<BanHistoryServiceImpl.BanHistoryDto> records;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static class IpQueryResultSwarms {
            @JsonProperty("total")
            private long total;
            @JsonProperty("records")
            private List<SwarmTrackerServiceImpl.SwarmTrackerDto> records;
            @JsonProperty("concurrent_download_torrents_count")
            private long concurrentDownloadTorrentsCount;
        }
    }

}
