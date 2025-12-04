package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PingConfigController extends BasePingController {
    @Autowired
    private IUserappConfigService userappConfigService;

    @GetMapping("/ping/config")
    public ResponseEntity<@NotNull BtnConfig> config() throws UserApplicationBannedException, UserApplicationNotFoundException, AccessDeniedException {
        Userapp userapp = verifyUserApplicationFailSafe();
        BtnConfig config;
        if (userapp == null) {
            config = userappConfigService.configAnonymousUserapp();
        } else {
            if (userapp.getBannedAt() != null) {
                throw new UserApplicationBannedException(userapp.getBannedReason());
            }
            config = userappConfigService.configLoggedInUserapp(userapp);
        }
        return ResponseEntity.ok(config);
    }
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Component
    public static class ReconfigureAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.reconfigure.interval}")
        private long interval;
        @Value("${sparkle.ping.reconfigure.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;
        @JsonProperty("version")
        private String version = UUID.randomUUID().toString();

        @Override
        public String getConfigKey() {
            return "reconfigure";
        }
    }
}
