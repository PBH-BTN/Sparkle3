package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.service.IPowCaptchaService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingCaptchaController extends BasePingController {
    @Autowired
    private IPowCaptchaService powCaptchaService;

    @GetMapping("/ping/captcha/createSession")
    public ResponseEntity<IPowCaptchaService.@NotNull CaptchaChallenge> createSession() {
        return ResponseEntity.ok(powCaptchaService.generateSession());
    }
}
