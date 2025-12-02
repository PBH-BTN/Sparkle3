package com.ghostchu.btn.sparkle.autoconfig;

import com.ghostchu.btn.sparkle.util.pow.PoWServer;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PoWCaptchaConfig {
    @Bean
    @NotNull
    public PoWServer poWServer(@Value("${sparkle.captcha.pow.difficulty-bits}") int difficultyBits, @Value("${sparkle.captcha.pow.algorithm}") String algorithm) {
        return new PoWServer(difficultyBits, algorithm);
    }
}
