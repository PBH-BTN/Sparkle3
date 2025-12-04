package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarmPeerPing;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.Data;
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

import java.util.Map;

@Slf4j
@RestController
public class PingSwarmController extends BasePingController {
    @Value("${sparkle.ping.sync-swarm.pow-captcha}")
    private boolean powCaptcha;
    @Autowired
    private ISwarmTrackerService swarmTrackerService;
    @Autowired
    private IClientDiscoveryService clientDiscoveryService;


    @PostMapping("/ping/syncSwarm")
    @Transactional
    public ResponseEntity<@NotNull String> onSwarmSync(@RequestBody BtnSwarmPeerPing ping) throws UserApplicationNotFoundException, UserApplicationBannedException, AccessDeniedException {
        if(powCaptcha){
            validatePowCaptcha();
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
        swarmTrackerService.syncSwarm(userapp.getId(), swarms);
        clientDiscoveryService.handleClientDiscovery(userapp.getId(), swarms.stream().map(ban -> Map.entry(ban.getPeerId(), ban.getClientName())).toList());
        return ResponseEntity.status(200).build();
    }

    @Component
    @Data
    public static class SwarmSyncBtnAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.sync-swarm.endpoint}")
        private String endpoint;
        @Value("${sparkle.ping.sync-swarm.interval}")
        private long interval;
        @Value("${sparkle.ping.sync-swarm.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;
        @Value("${sparkle.ping.sync-swarm.pow-captcha}")
        @JsonProperty("pow_captcha")
        private boolean powCaptcha;

        @Override
        public String getConfigKey() {
            return "submit_swarm";
        }
    }
}
