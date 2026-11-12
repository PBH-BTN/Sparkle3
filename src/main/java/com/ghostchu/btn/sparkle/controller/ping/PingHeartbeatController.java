package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.PowCaptchaFailureException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.btnability.DynamicSparkleBtnAbility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;

@RestController
@Slf4j
public class PingHeartbeatController extends BasePingController {
    @Autowired
    private IUserappsHeartbeatService heartbeatService;
    @Autowired
    private BtnRuntimeConfigService configService;

    @PostMapping("/ping/heartbeat")
    public ResponseEntity<@NotNull ServerResponse> onBansSync(@RequestBody IfAddrDto dto) throws UserApplicationNotFoundException, UserApplicationBannedException, AccessDeniedException, PowCaptchaFailureException {
        if (configService.booleanValue("heartbeat", "powCaptcha")
                && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        Userapp userapp = verifyUserApplicationForBatchedPoints();
        heartbeatService.onHeartBeat(
                userapp.getId(),
                userapp.getOwner(),
                InetAddress.ofLiteral(request.getRemoteAddr())
        );
        return ResponseEntity.ok(new ServerResponse(request.getRemoteAddr()));
    }

    @AllArgsConstructor
    @Data
    public static class ServerResponse {
        @JsonProperty("external_ip")
        private String externalIp;
    }


    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class IfAddrDto {
        @JsonProperty("ifaddr")
        private String ifaddr;
    }

    @Component
    public static class HeartbeatBtnAbility extends DynamicSparkleBtnAbility {
        @JsonProperty("endpoint")
        public String getEndpoint() {
            return text("heartbeat", "endpoint");
        }

        @JsonProperty("interval")
        public long getInterval() {
            return longValue("heartbeat", "interval");
        }

        @JsonProperty("random_initial_delay")
        public long getRandomInitialDelay() {
            return longValue("heartbeat", "randomInitialDelay");
        }

        @JsonProperty("pow_captcha")
        public boolean isPowCaptcha() {
            return booleanValue("heartbeat", "powCaptcha");
        }

        @JsonProperty("multi_if")
        public boolean isMultiIf() {
            return booleanValue("heartbeat", "multiIf");
        }

        @Override
        public String getConfigKey() {
            return "heartbeat";
        }
    }
}
