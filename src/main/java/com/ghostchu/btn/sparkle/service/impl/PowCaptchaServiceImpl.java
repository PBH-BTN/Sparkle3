package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.service.IPowCaptchaService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.util.pow.PoWServer;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PowCaptchaServiceImpl implements IPowCaptchaService {
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private BtnRuntimeConfigService configService;
    private final SecureRandom random = new SecureRandom();

    @Override
    public @NotNull CaptchaChallenge generateSession() {
        String id = UUID.randomUUID().toString();
        byte[] challenge = new byte[16];
        random.nextBytes(challenge);
        String base64Challenge = java.util.Base64.getEncoder().encodeToString(challenge);
        int difficultyBits = configService.intValue("captchaPow", "difficultyBits");
        String algorithm = supportedAlgorithm(
                configService.text("captchaPow", "algorithm")
        );
        String sessionValue = base64Challenge + "|" + difficultyBits + "|" + algorithm;
        redisTemplate.opsForValue().set(
                "sparkle:powcaptcha:" + id,
                sessionValue,
                20 * 60 * 1000,
                TimeUnit.MILLISECONDS
        );
        return new CaptchaChallenge(
                id,
                base64Challenge,
                difficultyBits,
                algorithm,
                System.currentTimeMillis() + 20 * 60 * 1000
        );
    }

    @Override
    public boolean validateSession(@NotNull String id , @NotNull String solutionBase64) {
        String sessionValue = redisTemplate.opsForValue().get("sparkle:powcaptcha:" + id);
        if (sessionValue == null) {
            return false;
        }
        try {
            String[] parts = sessionValue.split("\\|", 3);
            String challengeBase64 = parts[0];
            int difficultyBits = parts.length >= 2
                    ? Integer.parseInt(parts[1])
                    : configService.intValue("captchaPow", "difficultyBits");
            String algorithm = parts.length >= 3
                    ? parts[2]
                    : supportedAlgorithm(configService.text("captchaPow", "algorithm"));
            byte[] challengeBytes = Base64.getDecoder().decode(challengeBase64);
            byte[] solutionBytes = Base64.getDecoder().decode(solutionBase64);
            boolean result = new PoWServer(difficultyBits, algorithm)
                    .verify(challengeBytes, solutionBytes);
            if (result) {
                redisTemplate.delete("sparkle:powcaptcha:" + id);
            }
            return result;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String supportedAlgorithm(String algorithm) {
        try {
            MessageDigest.getInstance(algorithm);
            return algorithm;
        } catch (NoSuchAlgorithmException exception) {
            return "SHA-256";
        }
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
