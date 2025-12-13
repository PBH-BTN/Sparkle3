package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBanPing;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class PingSubmitBansController extends BasePingController {
    @Value("${sparkle.ping.sync-banhistory.pow-captcha}")
    private boolean powCaptcha;
    @Autowired
    private IBanHistoryService banHistoryService;
    @Autowired
    private IClientDiscoveryService clientDiscoveryService;

    @PostMapping("/ping/syncBanHistory")
    @Transactional
    public ResponseEntity<@NotNull String> onBansSync(@RequestBody BtnBanPing ping) throws UserApplicationNotFoundException, UserApplicationBannedException, AccessDeniedException {
        if(powCaptcha){
            validatePowCaptcha();
        }
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
            ban.setDescription(sanitizeU0(ban.getDescription()));
        }
        clientDiscoveryService.handleClientDiscovery(userapp.getId(), bans.stream().map(ban -> Pair.of(ban.getPeerId(), ban.getPeerClientName())).toList());
        banHistoryService.syncBanHistory(request.getRemoteAddr(), userapp.getId(), bans);
        return ResponseEntity.status(200).build();
    }
    @Component
    @Data
    public static class SyncBanHistoryBtnAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.sync-banhistory.endpoint}")
        public String endpoint;
        @Value("${sparkle.ping.sync-banhistory.interval}")
        public long interval;
        @Value("${sparkle.ping.sync-banhistory.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        public long randomInitialDelay;
        @Value("${sparkle.ping.sync-banhistory.pow-captcha}")
        @JsonProperty("pow_captcha")
        public boolean powCaptcha;

        @Override
        public String getConfigKey() {
            return "submit_bans";
        }
    }
}
