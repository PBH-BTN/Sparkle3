package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingConfigController extends BasePingController {
    @Autowired
    private IUserappConfigService userappConfigService;

    @GetMapping("/ping/config")
    public ResponseEntity<@NotNull BtnConfig> config() throws UserApplicationBannedException, UserApplicationNotFoundException {
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
//
//    public ResponseEntity<Object> config2() throws AccessDeniedException, JsonProcessingException, UnknownHostException {
//        var cred = cred();
//        userApplicationService.setUserApplicationLastAccess(cred.getId());
//        var audit = new LinkedHashMap<String, Object>();
//        audit.put("appId", cred.getAppId());
//        if (isCredBanned(cred)) {
//            log.warn("[BANNED] [Ping] [{}] 正在以遭到封禁的 UserApplication 请求配置文件：(AppId={}, UA={})",
//                    ip(req), cred.getAppId(), ua(req));
//            audit.put("error", "UserApplication Banned");
//            auditService.log(req, "BTN_CONFIG", false, audit);
//            return ResponseEntity.status(403).body("UserApplication 已被管理员封禁，请与服务器管理员联系");
//        }
////        log.info("[OK] [Config] [{}] 响应配置元数据 (AppId={}, UA={})",
////                ip(req), cred.getAppId(), ua(req));
//        Map<String, Object> rootObject = new HashMap<>();
//        rootObject.put("min_protocol_version", pingService.getMinProtocolVersion());
//        rootObject.put("max_protocol_version", pingService.getMaxProtocolVersion());
//
//        Map<String, Object> abilityObject = new HashMap<>();
//        rootObject.put("ability", abilityObject);
//        //abilityObject.put("submit_peers", submitPeersAbility);
//        abilityObject.put("submit_bans", submitBansAbility);
//        abilityObject.put("submit_histories", submitHistoriesAbility);
//        abilityObject.put("reconfigure", reconfigureAbility);
//        abilityObject.put("rules", cloudRuleAbility);
//        auditService.log(req, "BTN_CONFIG", true, audit);
//        var json = objectMapper.writeValueAsString(rootObject);
//        var countryIso = geoIPManager.geoData(InetAddress.getByName(ip(req))).getCountryIso();
//        if (countryIso != null && countryIso.equalsIgnoreCase("CN")) {
//            json = json.replace(sparkleRoot, sparkleRootChina);
//        }
//        return ResponseEntity.ok().body(json);
//    }


}
