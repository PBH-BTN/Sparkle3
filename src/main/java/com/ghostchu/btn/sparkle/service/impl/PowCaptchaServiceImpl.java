package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.service.IPowCaptchaService;
import com.ghostchu.btn.sparkle.util.pow.PoWServer;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

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
        String challenge = getRandomString(32);
        redisTemplate.opsForValue().set("sparkle:powcaptcha:" + id, challenge);
        return new CaptchaChallenge(id, challenge, poWServer.getDifficultyBits(), poWServer.getAlgorithm(), System.currentTimeMillis() + 20 * 60 * 1000);
    }

    @Override
    public boolean validateSession(@NotNull String id, @NotNull String solution) {
        String challenge = redisTemplate.opsForValue().get("sparkle:powcaptcha:" + id);
        if (challenge == null) {
            return false;
        }
        boolean result = poWServer.verify(challenge.getBytes(StandardCharsets.UTF_8), solution.getBytes(StandardCharsets.UTF_8));
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
