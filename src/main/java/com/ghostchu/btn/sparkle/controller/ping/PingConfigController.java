package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.btnability.DynamicSparkleBtnAbility;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

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
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BtnRuntimeConfigService configService;

    @GetMapping("/ping/config")
    public ResponseEntity<@NotNull String> config(@RequestParam(value = "forceRoute", required = false) String forceRoute) throws UserApplicationBannedException, UserApplicationNotFoundException, AccessDeniedException {
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
            case "chinamainland" -> configService.text("rootUrls", "chnRootUrl");
            case "global" -> configService.text("rootUrls", "rootUrl");
            default -> detectRoute(InetAddress.ofLiteral(request.getRemoteAddr()));
        };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(config).replace("{rooturl}", routeUrl));
    }

    private final static List<String> cnOptimizeProvinces = List.of(
            "福建", "江苏", "浙江", "四川", "重庆", "河南", "湖北"
    );

    private String detectRoute(InetAddress inetAddress) {
        var geoData = ipdb.queryIPDB(inetAddress).geoData().get();
        if (geoData.getNetwork() != null && geoData.getNetwork().getIsp() != null) {
            if (geoData.getNetwork().getIsp().contains("移动")) {
                return configService.text("rootUrls", "chnRootUrl");
            }
        }
        if (geoData.getCity() != null && geoData.getCity().getCnProvince() != null) {
            for (String cnOptimizeProvince : cnOptimizeProvinces) {
                if(geoData.getCity().getCnProvince().contains(cnOptimizeProvince)){
                    return configService.text("rootUrls", "chnRootUrl");
                }
            }
        }
        return configService.text("rootUrls", "rootUrl");
    }

    @Component
    public static class ReconfigureAbility extends DynamicSparkleBtnAbility {
        private final String version = UUID.randomUUID().toString();

        @JsonProperty("interval")
        public long getInterval() {
            return longValue("reconfigure", "interval");
        }

        @JsonProperty("random_initial_delay")
        public long getRandomInitialDelay() {
            return longValue("reconfigure", "randomInitialDelay");
        }

        @JsonProperty("version")
        public String getVersion() {
            return version;
        }

        @Override
        public String getConfigKey() {
            return "reconfigure";
        }
    }
}
