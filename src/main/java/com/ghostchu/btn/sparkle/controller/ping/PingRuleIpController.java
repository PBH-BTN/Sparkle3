package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import com.ghostchu.btn.sparkle.service.impl.AnalyseRuleConcurrentDownloadServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.AnalyseRuleOverDownloadServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.AnalyseRuleUnTrustVoteServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.RuleServiceImpl;
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
import java.util.stream.Collectors;

@RestController
public class PingRuleIpController extends BasePingController {

    @Autowired
    private AnalyseRuleUnTrustVoteServiceImpl unTrustVoteService;
    @Autowired
    private AnalyseRuleOverDownloadServiceImpl overDownloadService;
    @Autowired
    private AnalyseRuleConcurrentDownloadServiceImpl concurrentDownloadService;
    @Autowired
    private RuleServiceImpl ruleService;


    @Value("${sparkle.ping.rule-ip-denylist.pow-captcha}")
    private boolean denyListPowCaptcha;

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @SuppressWarnings("UnstableApiUsage")
    @GetMapping("/ping/ruleIpDenylist")
    public ResponseEntity<@NotNull Object> ipDenyList(@RequestParam("rev") String version) {
        if (denyListPowCaptcha) {
            validatePowCaptcha();
        }
        String untrustedVote = unTrustVoteService.getGeneratedContent().getValue();
        String overDownloadedAnalyse = overDownloadService.getGeneratedContent().getValue();
        String concurrentDownloadAnalyse = concurrentDownloadService.getGeneratedContent().getValue();
        String manualRules = ruleService.getIpDenyList();
        StringJoiner joiner = new StringJoiner("\n\n");
        if (untrustedVote != null && !untrustedVote.isBlank())
            joiner.add(untrustedVote);
        if (overDownloadedAnalyse != null && !overDownloadedAnalyse.isBlank())
            joiner.add(overDownloadedAnalyse);
        if (concurrentDownloadAnalyse != null && !concurrentDownloadAnalyse.isBlank())
            joiner.add(concurrentDownloadAnalyse);
        if(manualRules != null && !manualRules.isBlank()){
            joiner.add(manualRules);
        }
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
        @Value("${sparkle.ping.rule-ip-denylist.pow-captcha}")
        @JsonProperty("pow_captcha")
        private boolean powCaptcha;

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
