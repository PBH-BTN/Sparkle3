package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import com.google.common.hash.Hashing;
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
import java.util.Objects;
import java.util.StringJoiner;

@RestController
public class PingRuleIpController extends BasePingController {

    @Autowired
    @Qualifier("genericStringStringTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @SuppressWarnings("UnstableApiUsage")
    @GetMapping("/ping/ruleIpDenylist")
    public ResponseEntity<@NotNull Object> ipDenyList(@RequestParam("rev") String version) {
        String untrustedVote = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE.getKey());
        String overDownloadedAnalyse = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_OVER_DOWNLOAD_VOTE_VALUE.getKey());

        StringJoiner joiner = new StringJoiner("\n\n");
        if(untrustedVote != null)
            joiner.add(untrustedVote);
        if(overDownloadedAnalyse != null)
            joiner.add(overDownloadedAnalyse);

        String listVersion = Hashing.crc32c().hashString(joiner.toString(), StandardCharsets.UTF_8).toString();

        if (Objects.equals(listVersion, version)) {
            return ResponseEntity.noContent().header("X-BTN-ContentVersion", listVersion).build();
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
        private String endpoint;
        @Value("${sparkle.ping.rule-ip-denylist.interval}")
        private long interval;
        @Value("${sparkle.ping.rule-ip-denylist.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;

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
