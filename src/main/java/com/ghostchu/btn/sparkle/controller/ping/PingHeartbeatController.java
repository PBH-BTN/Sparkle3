package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;

@RestController
@Slf4j
public class PingHeartbeatController extends BasePingController {
    @Value("${sparkle.ping.sync-banhistory.pow-captcha}")
    private boolean powCaptcha;
    @Autowired
    private IUserappsHeartbeatService heartbeatService;

    @PostMapping("/ping/heartbeat")
    @Transactional
    public ResponseEntity<@NotNull String> onBansSync(@RequestBody IfAddrDto dto) throws UserApplicationNotFoundException, UserApplicationBannedException, AccessDeniedException {
        if (powCaptcha) {
            validatePowCaptcha();
        }
        Userapp userapp = verifyUserApplication();
        heartbeatService.onHeartBeat(userapp.getId(), InetAddress.ofLiteral(request.getRemoteAddr()));
        return ResponseEntity.status(200).build();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class IfAddrDto {
        @JsonProperty("ifaddr")
        private String ifaddr;
    }

    @Component
    @Data
    public static class HeartbeatBtnAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.heartbeat.endpoint}")
        private String endpoint;
        @Value("${sparkle.ping.heartbeat.interval}")
        private long interval;
        @Value("${sparkle.ping.heartbeat.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;
        @Value("${sparkle.ping.heartbeat.pow-captcha}")
        @JsonProperty("pow_captcha")
        private boolean powCaptcha;
        @Value("${sparkle.ping.heartbeat.multi-if}")
        @JsonProperty("multi_if")
        private boolean multiIf;

        @Override
        public String getConfigKey() {
            return "heartbeat";
        }
    }
}
