package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.service.IPowCaptchaService;
import com.ghostchu.btn.sparkle.util.pow.PoWServer;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PowCaptchaServiceImpl implements IPowCaptchaService {
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private PoWServer poWServer;
    private final SecureRandom random = new SecureRandom();

    @Override
    public @NotNull CaptchaChallenge generateSession() {
        String id = UUID.randomUUID().toString();
        byte[] challenge = new byte[16];
        random.nextBytes(challenge);
        String base64Challenge = java.util.Base64.getEncoder().encodeToString(challenge);
        redisTemplate.opsForValue().set("sparkle:powcaptcha:" + id, base64Challenge, 20 * 60 * 1000, TimeUnit.MILLISECONDS);
        return new CaptchaChallenge(id, base64Challenge, poWServer.getDifficultyBits(), poWServer.getAlgorithm(), System.currentTimeMillis() + 20 * 60 * 1000);
    }

    @Override
    public boolean validateSession(@NotNull String id , @NotNull String solutionBase64) {
        String challengeBase64 = redisTemplate.opsForValue().get("sparkle:powcaptcha:" + id);
        if (challengeBase64 == null) {
            return false;
        }
        byte[] challengeBytes = Base64.getDecoder().decode(challengeBase64);
        byte[] solutionBytes = Base64.getDecoder().decode(solutionBase64);
        boolean result = poWServer.verify(challengeBytes, solutionBytes);
        if (result) {
            redisTemplate.delete("sparkle:powcaptcha:" + id);
        }
        return result;
    }

    @NotNull
    public String getRandomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }
}
