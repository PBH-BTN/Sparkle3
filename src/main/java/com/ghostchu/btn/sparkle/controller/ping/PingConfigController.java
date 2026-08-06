package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
public class PingConfigController extends BasePingController {
    @Autowired
    private IUserappConfigService userappConfigService;
    @Autowired
    private IPDBManager ipdb;
    @Value("${sparkle.root-url}")
    private String rootUrl;
    @Value("${sparkle.chn-root-url}")
    private String chnRootUrl;

    @GetMapping("/ping/config")
    public ResponseEntity<@NotNull BtnConfig> config(@RequestParam(value = "forceRoute", required = false) String forceRoute) throws UserApplicationBannedException, UserApplicationNotFoundException, AccessDeniedException {
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
        if (forceRoute == null) {
            forceRoute = ""; // go default case
        }
        String routeUrl = switch (forceRoute) {
            case "chinamainland" -> chnRootUrl;
            case "global" -> rootUrl;
            default -> detectRoute(InetAddress.ofLiteral(request.getRemoteAddr()));
        };
        for (SparkleBtnAbility ability : config.getAbility().values()) {
            // get endpoint private field content
            try {
                var field = ability.getClass().getDeclaredField("endpoint");
                String endpoint = (String) field.get(ability);
                if (endpoint != null) {
                    String newEndpoint = endpoint.replace("{rooturl}", routeUrl);
                    field.set(ability, newEndpoint);
                    //log.info("Replaced endpoint for ability {} from {} to {} for CN user", ability.getConfigKey(), endpoint, newEndpoint);
                }
            } catch (NoSuchFieldException e) {
                //log.warn("Field 'endpoint' not found in ability class: {}", ability.getClass().getName(), e);
            } catch (IllegalAccessException e) {
                log.warn("Failed to access field 'endpoint' in ability class: {}", ability.getClass().getName(), e);
            }
        }
        var powEndpoint = config.getProofOfWorkConfig().getEndpoint();
        config.getProofOfWorkConfig().setEndpoint(powEndpoint.replace("{rooturl}", routeUrl));
        return ResponseEntity.ok(config);
    }

    private final static List<String> cnOptimizeProvinces = List.of(
            "福建", "江苏", "浙江", "四川", "重庆", "河南", "湖北"
    );

    private String detectRoute(InetAddress inetAddress) {
        var geoData = ipdb.queryIPDB(inetAddress).geoData().get();
        if (geoData.getNetwork() != null && geoData.getNetwork().getIsp() != null) {
            if (geoData.getNetwork().getIsp().contains("移动")) {
                return chnRootUrl;
            }
        }
        if (geoData.getCity() != null && geoData.getCity().getCnProvince() != null) {
            if (cnOptimizeProvinces.contains(geoData.getCity().getCnProvince())) {
                return chnRootUrl;
            }
        }
        return rootUrl;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Component
    public static class ReconfigureAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.reconfigure.interval}")
        public long interval;
        @Value("${sparkle.ping.reconfigure.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        public long randomInitialDelay;
        @JsonProperty("version")
        public String version = UUID.randomUUID().toString();

        @Override
        public String getConfigKey() {
            return "reconfigure";
        }
    }
}
