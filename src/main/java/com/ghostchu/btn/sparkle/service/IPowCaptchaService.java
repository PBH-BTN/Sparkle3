package com.ghostchu.btn.sparkle.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

public interface IPowCaptchaService {
    @NotNull CaptchaChallenge generateSession();

    boolean validateSession(@NotNull String id, String solutionBase64);

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    class CaptchaChallenge {
        private String id;
        private String challengeBase64;
        private int difficultyBits;
        private String algorithm;
        private long expireAt;
    }
}
