package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnRuleset;
import com.ghostchu.btn.sparkle.service.IRuleService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class PingRulePeerIdentityController extends BasePingController {

    @Value("${sparkle.ping.rule-peer-identity.pow-captcha}")
    private boolean powCaptcha;

    @Autowired
    private IRuleService ruleService;

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @GetMapping("/ping/rulePeerIdentity")
    public ResponseEntity<@NotNull Object> rulePeerIdentity(@RequestParam("rev") String version) {
        if(powCaptcha){
            validatePowCaptcha();
        }
        var peerId = ruleService.getRulesByType("peer_id");
        var clientName = ruleService.getRulesByType("client_name");
        var port = ruleService.getRulesByType("port");
        var script = ruleService.getRulesByType("script");

        Map<String, List<String>> peerIdRules = new HashMap<>();
        Map<String, List<String>> clientNameRules = new HashMap<>();
        Map<String, List<Integer>> portRules = new HashMap<>();
        Map<String, String> scriptRules = new HashMap<>();

        peerId.forEach(rule -> peerIdRules.compute(rule.getCategory(), (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(rule.getContent());
            return v;
        }));
        clientName.forEach(rule -> clientNameRules.compute(rule.getCategory(), (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(rule.getContent());
            return v;
        }));
        port.forEach(rule -> portRules.compute(rule.getCategory(), (k, v) -> {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(Integer.parseInt(rule.getContent()));
            return v;
        }));
        script.forEach(rule -> scriptRules.put(rule.getCategory(), rule.getContent()));
        BtnRuleset ruleset = new BtnRuleset();
        ruleset.setPeerIdRules(peerIdRules);
        ruleset.setClientNameRules(clientNameRules);
        ruleset.setPortRules(portRules);
        ruleset.setScriptRules(scriptRules);
        int ver = Objects.hash(peerId, clientName, port, script);
        ruleset.setVersion(String.valueOf(ver));
        return ResponseEntity.ok().header("X-BTN-ContentVersion", String.valueOf(ver)).body(ruleset);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Component
    public static class RulePeerIdentityBtnModule implements SparkleBtnAbility {
        @Value("${sparkle.ping.rule-peer-identity.endpoint}")
        private String endpoint;
        @Value("${sparkle.ping.rule-peer-identity.interval}")
        private long interval;
        @Value("${sparkle.ping.rule-peer-identity.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;
        @Value("${sparkle.ping.rule-peer-identity.pow-captcha}")
        @JsonProperty("pow_captcha")
        private boolean powCaptcha;

        @Override
        public String getConfigKey() {
            return "rule_peer_identity";
        }
    }

//
//    @AllArgsConstructor
//    @NoArgsConstructor
//    @Data
//    public static class IpAllowListBtnModule implements SparkleBtnAbility{
//        @Value("${sparkle.ping.rule-ip-denylist.endpoint}")
//        private String endpoint;
//        @Value("${sparkle.ping.rule-ip-denylist.interval}")
//        private long interval;
//        @Value("${sparkle.ping.rule-ip-denylist.random-initial-delay}")
//        @JsonProperty("random_initial_delay")
//        private long randomInitialDelay;
//
//        @Override
//        public String getConfigKey() {
//            return "ip_allowlist";
//        }
//    }
}
