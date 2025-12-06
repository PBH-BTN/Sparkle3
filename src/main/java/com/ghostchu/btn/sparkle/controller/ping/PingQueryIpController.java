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
import com.ghostchu.btn.sparkle.service.impl.QueryIpServiceImpl;
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
    @Autowired
    private QueryIpServiceImpl queryIpService;

    @GetMapping("/ping/queryIp")
    //@Cacheable(value = "pingQueryIpCache#600000", key = "#ip", unless = "#result == null || !#result.statusCode.is2xxSuccessful()")
    public ResponseEntity<QueryIpServiceImpl.@NotNull IpQueryResult> queryIp(@RequestParam String ip) throws AccessDeniedException, PowCaptchaFailureException, UserApplicationBannedException, UserApplicationNotFoundException {
        if (powCaptcha && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        var userApps = verifyUserApplication();
        var peerIp = InetAddress.ofLiteral(ip);
        return ResponseEntity.ok(queryIpService.queryIp(peerIp));
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
        @Value("${sparkle.ping.query-ip.iframe-endpoint}")
        @JsonProperty("iframe_endpoint")
        private String iframeEndpoint;

        @Override
        public String getConfigKey() {
            return "ip_query";
        }
    }

}
