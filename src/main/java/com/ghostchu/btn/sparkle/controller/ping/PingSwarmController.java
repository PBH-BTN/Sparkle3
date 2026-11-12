package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarmPeerPing;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.PowCaptchaFailureException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.btnability.DynamicSparkleBtnAbility;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class PingSwarmController extends BasePingController {
    @Autowired
    private ISwarmTrackerService swarmTrackerService;
    @Autowired
    private IClientDiscoveryService clientDiscoveryService;
    @Autowired
    private BtnRuntimeConfigService configService;


    @PostMapping("/ping/syncSwarm")
    public ResponseEntity<@NotNull String> onSwarmSync(@RequestBody BtnSwarmPeerPing ping) throws UserApplicationNotFoundException, UserApplicationBannedException, AccessDeniedException, PowCaptchaFailureException {
        if (configService.booleanValue("syncSwarm", "powCaptcha")
                && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        Userapp userapp = verifyUserApplication();
        var swarms = ping.getSwarms();
        var it = swarms.iterator();
        while (it.hasNext()) {
            var swarm = it.next();
            if (!isAcceptablePublicIp(swarm.getPeerIp())) {
                log.debug("Reject swarm peer with unacceptable IP: {}", swarm.getPeerIp());
                it.remove();
                continue;
            }
            swarm.setDownloader(sanitizeU0(swarm.getDownloader()));
            swarm.setPeerId(cutPeerId(sanitizeU0(swarm.getPeerId())));
            swarm.setClientName(sanitizeU0(swarm.getClientName()));
            swarm.setPeerLastFlags(sanitizeU0(swarm.getPeerLastFlags()));
        }
        swarmTrackerService.syncSwarm(userapp.getId(), userapp.getOwner(), swarms);
        clientDiscoveryService.handleClientDiscovery(
                userapp.getId(),
                userapp.getOwner(),
                swarms.stream().map(ban -> Pair.of(ban.getPeerId(), ban.getClientName())).toList()
        );
        return ResponseEntity.status(200).build();
    }

    @Component
    public static class SwarmSyncBtnAbility extends DynamicSparkleBtnAbility {
        @JsonProperty("endpoint")
        public String getEndpoint() {
            return text("syncSwarm", "endpoint");
        }

        @JsonProperty("interval")
        public long getInterval() {
            return longValue("syncSwarm", "interval");
        }

        @JsonProperty("random_initial_delay")
        public long getRandomInitialDelay() {
            return longValue("syncSwarm", "randomInitialDelay");
        }

        @JsonProperty("pow_captcha")
        public boolean isPowCaptcha() {
            return booleanValue("syncSwarm", "powCaptcha");
        }

        @Override
        public String getConfigKey() {
            return "submit_swarm";
        }
    }
}
