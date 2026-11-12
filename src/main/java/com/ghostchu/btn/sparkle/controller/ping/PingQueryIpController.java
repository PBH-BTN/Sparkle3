package com.ghostchu.btn.sparkle.controller.ping;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.PowCaptchaFailureException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.btnability.DynamicSparkleBtnAbility;
import com.ghostchu.btn.sparkle.service.impl.QueryIpServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingQueryIpController extends BasePingController {
    @Autowired
    private QueryIpServiceImpl queryIpService;
    @Autowired
    private BtnRuntimeConfigService configService;

    @GetMapping("/ping/queryIp")
    public ResponseEntity<QueryIpServiceImpl.@NotNull IpQueryResult> queryIp(@RequestParam String ip) throws AccessDeniedException, PowCaptchaFailureException, UserApplicationBannedException, UserApplicationNotFoundException {
        if (configService.booleanValue("queryIp", "powCaptcha") && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        var userApps = verifyUserApplication();
        return ResponseEntity.ok(queryIpService.queryIp(ip));
    }

    @GetMapping("/ping/queryIp/widget/data")
    public ResponseEntity<QueryIpServiceImpl.@NotNull IpQueryResult> queryIpWidget(@RequestParam String ip)
            throws AccessDeniedException, UserApplicationBannedException, UserApplicationNotFoundException {
        verifyUserApplication();
        return ResponseEntity.ok(queryIpService.queryIp(ip));
    }

    @Component
    public static class IpQueryBtnModule extends DynamicSparkleBtnAbility {
        @JsonProperty("endpoint")
        public String getEndpoint() {
            return text("queryIp", "endpoint");
        }

        @JsonProperty("pow_captcha")
        public boolean isPowCaptcha() {
            return booleanValue("queryIp", "powCaptcha");
        }

        @JsonProperty("iframe_endpoint")
        public String getIframeEndpoint() {
            return text("queryIp", "iframeEndpoint");
        }

        @Override
        public String getConfigKey() {
            return "ip_query";
        }
    }

}
