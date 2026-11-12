package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.exception.PowCaptchaFailureException;
import com.ghostchu.btn.sparkle.service.btnability.IPAllowListRuleProvider;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.btnability.DynamicSparkleBtnAbility;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.service.points.PointAwardContext;
import com.ghostchu.btn.sparkle.util.HexUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private List<IPAllowListRuleProvider> ipAllowListRuleProviders;


    @Autowired
    private BtnRuntimeConfigService configService;

    @Autowired
    private PointAwardContext pointAwardContext;

    @GetMapping("/ping/ruleIpDenylist")
    public ResponseEntity<@NotNull Object> ipDenyList(@RequestParam("rev") String version) throws NoSuchAlgorithmException, PowCaptchaFailureException {
        if (configService.booleanValue("ruleIpDenylist", "powCaptcha")
                && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        var userapp = authenticateForRulesPoints();
        if (userapp != null) {
            pointAwardContext.recordConfigured(
                    userapp.getOwner(),
                    PointCategory.RULES_FETCH,
                    1,
                    0
            );
        }
        MessageDigest versionDigest = MessageDigest.getInstance("SHA-256");
        for (IPDenyListRuleProvider ipDenyListRuleProvider : ipDenyListRuleProviders) {
            var v = ipDenyListRuleProvider.getVersion();
            if (v == null) continue;
            versionDigest.update(v.getBytes(StandardCharsets.UTF_8));
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

    @GetMapping("/ping/ruleIpAllowlist")
    public ResponseEntity<@NotNull String> ipAllowList(@RequestParam("rev") String version) throws NoSuchAlgorithmException, PowCaptchaFailureException {
        if (configService.booleanValue("ruleIpAllowlist", "powCaptcha")
                && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        var userapp = authenticateForRulesPoints();
        if (userapp != null) {
            pointAwardContext.recordConfigured(
                    userapp.getOwner(),
                    PointCategory.RULES_FETCH,
                    1,
                    0
            );
        }
        MessageDigest versionDigest = MessageDigest.getInstance("SHA-256");
        for (IPAllowListRuleProvider ipAllowListRuleProvider : ipAllowListRuleProviders) {
            var v = ipAllowListRuleProvider.getVersion();
            if (v == null) continue;
            versionDigest.update(v.getBytes(StandardCharsets.UTF_8));
        }
        byte[] digest = versionDigest.digest();
        String listVersion = HexUtil.bytesToHex(digest);
        if (Objects.equals(listVersion, version)) {
            return ResponseEntity.noContent().header("X-BTN-ContentVersion", listVersion).build();
        }
        StringJoiner joiner = new StringJoiner("\n\n");
        for (IPAllowListRuleProvider ipAllowListRuleProvider : ipAllowListRuleProviders) {
            var content = ipAllowListRuleProvider.getContent();
            if (content != null && !content.isBlank()) {
                joiner.add(content);
            }
        }
        return ResponseEntity.status(200).header("X-BTN-ContentVersion", listVersion)
                .contentType(MediaType.TEXT_PLAIN).body(joiner.toString());
    }

    @Component
    public static class IpDenyListBtnModule extends DynamicSparkleBtnAbility {
        @JsonProperty("endpoint")
        public String getEndpoint() {
            return text("ruleIpDenylist", "endpoint");
        }

        @JsonProperty("interval")
        public long getInterval() {
            return longValue("ruleIpDenylist", "interval");
        }

        @JsonProperty("random_initial_delay")
        public long getRandomInitialDelay() {
            return longValue("ruleIpDenylist", "randomInitialDelay");
        }

        @JsonProperty("pow_captcha")
        public boolean isPowCaptcha() {
            return booleanValue("ruleIpDenylist", "powCaptcha");
        }

        @Override
        public String getConfigKey() {
            return "ip_denylist";
        }
    }

    @Component
    public static class IpAllowListBtnModule extends DynamicSparkleBtnAbility {
        @JsonProperty("endpoint")
        public String getEndpoint() {
            return text("ruleIpAllowlist", "endpoint");
        }

        @JsonProperty("interval")
        public long getInterval() {
            return longValue("ruleIpAllowlist", "interval");
        }

        @JsonProperty("random_initial_delay")
        public long getRandomInitialDelay() {
            return longValue("ruleIpAllowlist", "randomInitialDelay");
        }

        @JsonProperty("pow_captcha")
        public boolean isPowCaptcha() {
            return booleanValue("ruleIpAllowlist", "powCaptcha");
        }

        @Override
        public String getConfigKey() {
            return "ip_allowlist";
        }
    }
}
