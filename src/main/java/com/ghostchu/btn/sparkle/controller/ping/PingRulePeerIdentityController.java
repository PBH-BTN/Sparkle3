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
public class PingRulePeerIdentityController extends BasePingController {

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @SuppressWarnings("UnstableApiUsage")
    @GetMapping("/ping/rulePeerIdentity")
    public ResponseEntity<@NotNull Object> ipDenyList(@RequestParam("rev") String version) {

    }

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
