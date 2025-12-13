package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import com.ghostchu.btn.sparkle.util.ipdb.IPDB;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@RestController
public class PingConfigController extends BasePingController {
    @Autowired
    private IUserappConfigService userappConfigService;
    @Autowired
    private GeoIPManager ipdb;
    @Value("${sparkle.root-url}")
    private String rootUrl;
    @Value("${sparkle.chn-root-url}")
    private String chnRootUrl;


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
        var geoData = ipdb.geoData(InetAddress.ofLiteral(request.getRemoteAddr()));
        if (geoData != null && geoData.getCountryIso() != null && "cn".equals(geoData.getCountryIso().toLowerCase(Locale.ROOT))) {
            if (chnRootUrl != null && !chnRootUrl.isBlank()) {
                for (SparkleBtnAbility ability : config.getAbility().values()) {
                    // get endpoint private field content
                    try {
                        var field = ability.getClass().getDeclaredField("endpoint");
                        String endpoint = (String) field.get(ability);
                        if (endpoint != null && endpoint.startsWith(rootUrl)) {
                            String newEndpoint = endpoint.replaceFirst(rootUrl, chnRootUrl);
                            field.set(ability, newEndpoint);
                            log.info("Replaced endpoint for ability {} from {} to {} for CN user", ability.getConfigKey(), endpoint, newEndpoint);
                        }
                    } catch (NoSuchFieldException e) {
                        log.warn("Field 'endpoint' not found in ability class: {}", ability.getClass().getName(), e);
                    } catch (IllegalAccessException e) {
                        log.warn("Failed to access field 'endpoint' in ability class: {}", ability.getClass().getName(), e);
                    }
                }
            }
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
