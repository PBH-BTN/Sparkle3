package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import com.ghostchu.btn.sparkle.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@RestController
public class PingRuleIpController extends BasePingController {

    @Autowired
    private List<IPDenyListRuleProvider> ipDenyListRuleProviders;


    @Value("${sparkle.ping.rule-ip-denylist.pow-captcha}")
    private boolean denyListPowCaptcha;

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @GetMapping("/ping/ruleIpDenylist")
    public ResponseEntity<@NotNull Object> ipDenyList(@RequestParam("rev") String version) throws NoSuchAlgorithmException {
        if (denyListPowCaptcha) {
            validatePowCaptcha();
        }
        MessageDigest versionDigest = MessageDigest.getInstance("SHA-256");
        for (IPDenyListRuleProvider ipDenyListRuleProvider : ipDenyListRuleProviders) {
            versionDigest.update(ipDenyListRuleProvider.getVersion().getBytes(StandardCharsets.UTF_8));
        }
        byte[] digest = versionDigest.digest();
        String listVersion = HexUtil.bytesToHex(digest);
        if (Objects.equals(listVersion, version)) {
            return ResponseEntity.noContent().header("X-BTN-ContentVersion", listVersion).build();
        }
        StringJoiner joiner = new StringJoiner("\n\n");
        for (IPDenyListRuleProvider ipDenyListRuleProvider : ipDenyListRuleProviders) {
            var content = ipDenyListRuleProvider.getContent();
            if (content != null && !content.isBlank()) {
                joiner.add(content);
            }
        }
        return ResponseEntity.status(200).header("X-BTN-ContentVersion", listVersion)
                .contentType(MediaType.TEXT_PLAIN).body(joiner.toString());
    }

//    @SuppressWarnings("UnstableApiUsage")
//    @GetMapping("/ping/ruleIpDAllowlist")
//    public ResponseEntity<@NotNull Object> ipAllowList(@RequestParam("rev") String version) {
//        StringJoiner joiner = new StringJoiner("\n\n");
//
//        String listVersion = Hashing.crc32c().hashString(joiner.toString(), StandardCharsets.UTF_8).toString();
//
//        if(Objects.equals(listVersion, version)){
//            return ResponseEntity.noContent().header("X-BTN-ContentVersion", listVersion).build();
//        }
//
//        return ResponseEntity.status(200).header("X-BTN-ContentVersion", listVersion)
//                .contentType(MediaType.TEXT_PLAIN).body(joiner.toString());
//    }
//
//    @GetMapping("/ping/rule/ruleIpAllowlist")
//    public ResponseEntity<@NotNull String> ipAllowList() {
//        return ResponseEntity.noContent().build();
//    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Component
    public static class IpDenyListBtnModule implements SparkleBtnAbility {
        @Value("${sparkle.ping.rule-ip-denylist.endpoint}")
        public String endpoint;
        @Value("${sparkle.ping.rule-ip-denylist.interval}")
        public long interval;
        @Value("${sparkle.ping.rule-ip-denylist.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        public long randomInitialDelay;
        @Value("${sparkle.ping.rule-ip-denylist.pow-captcha}")
        @JsonProperty("pow_captcha")
        public boolean powCaptcha;

        @Override
        public String getConfigKey() {
            return "ip_denylist";
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
