package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBanPing;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Slf4j
public class PingSubmitBansController extends BasePingController {
    @Autowired
    private IBanHistoryService banHistoryService;
    @Autowired
    private IClientDiscoveryService clientDiscoveryService;

    @PostMapping("/ping/syncBanHistory")
    @Transactional
    public ResponseEntity<@NotNull String> onBansSync(@RequestBody BtnBanPing ping) throws UserApplicationNotFoundException, UserApplicationBannedException, AccessDeniedException {
        Userapp userapp = verifyUserApplication();
        var bans = ping.getBans();
        var it = bans.iterator();
        while (it.hasNext()) {
            var ban = it.next();
            if (!isAcceptablePublicIp(ban.getPeerIp())) {
                log.debug("Reject ban entry with unacceptable IP: {}", ban.getPeerIp());
                it.remove();
                continue;
            }
            ban.setPeerId(cutPeerId(sanitizeU0(ban.getPeerId())));
            ban.setPeerClientName(sanitizeU0(ban.getPeerClientName()));
            ban.setModule(sanitizeU0(ban.getModule()));
            ban.setRule(sanitizeU0(ban.getRule()));
            ban.setPeerFlag(sanitizeU0(ban.getPeerFlag()));
            ban.setStructuredData(sanitizeU0(ban.getStructuredData()));
        }
        clientDiscoveryService.handleClientDiscovery(userapp.getId(), bans.stream().map(ban -> Map.entry(ban.getPeerId(), ban.getPeerClientName())).toList());
        banHistoryService.syncBanHistory(request.getRemoteAddr(), userapp.getId(), bans);
        return ResponseEntity.status(200).build();
    }
}
