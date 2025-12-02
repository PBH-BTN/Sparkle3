package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarmPeerPing;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
public class PingSwarmController extends BasePingController {
    @Autowired
    private ISwarmTrackerService swarmTrackerService;
    @Autowired
    private IClientDiscoveryService clientDiscoveryService;


    @PostMapping("/ping/syncSwarm")
    @Transactional
    public ResponseEntity<@NotNull String> onSwarmSync(@RequestBody BtnSwarmPeerPing ping) throws UserApplicationNotFoundException, UserApplicationBannedException {
        Userapp userapp = verifyUserApplication();
        var swarms = ping.getSwarms();
        var it = swarms.iterator();
        while (it.hasNext()) {
            var swarm = it.next();
            if (!isAcceptablePublicIp(swarm.getPeerIp())) {
                log.warn("Reject swarm peer with unacceptable IP: {}", swarm.getPeerIp());
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
}
