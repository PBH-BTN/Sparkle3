package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import org.springframework.web.server.ResponseStatusException;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.ghostchu.btn.sparkle.controller.ui.dto.MailUnsubscribeModels.Option;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
public class MailUnsubscribeService {
    private final RedisTemplate<String, String> redis;
    private final IUserService users;
    private final NotificationPreferenceService preferences;
    private final BtnRuntimeConfigService config;

    public MailUnsubscribeService(@Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redis,
                                  IUserService users, NotificationPreferenceService preferences,
                                  BtnRuntimeConfigService config) {
        this.redis = redis;
        this.users = users;
        this.preferences = preferences;
        this.config = config;
    }

    public String link(long userId, String email, NotificationCategory category) {
        if (category.isMandatory()) return null;
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), userId + ":" + digest(email), Duration.ofDays(90));
        return config.text("rootUrls", "rootUrl").replaceAll("/+$", "")
                + "/mail-unsubscribe#token=" + token + "&category=" + category.name();
    }

    public List<Option> options(String token) {
        return preferences.list(userId(token)).stream().filter(p -> !p.locked())
                .map(p -> new Option(p.category().name(), p.emailEnabled())).toList();
    }

    public void unsubscribe(String token, List<String> categories) {
        long userId = userId(token);
        if (categories == null || categories.size() > NotificationCategory.values().length) throw invalid();
        List<NotificationCategory> parsed = categories.stream().map(value ->
                NotificationCategory.parse(value).filter(c -> !c.isMandatory()).orElseThrow(this::invalid)).toList();
        preferences.disableEmail(userId, parsed);
    }

    private long userId(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String stored = redis.opsForValue().get(key(token));
        if (stored == null) throw invalid();
        String[] parts = stored.split(":", 2);
        var user = users.getById(Long.parseLong(parts[0]));
        if (user == null || user.getEmail() == null || !MessageDigest.isEqual(
                parts[1].getBytes(StandardCharsets.UTF_8), digest(user.getEmail()).getBytes(StandardCharsets.UTF_8))) throw invalid();
        return user.getId();
    }

    private String key(String token) {
        return RedisKeyConstant.MAIL_UNSUBSCRIBE.getKey() + ":" + digest(token);
    }

    private String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "退订链接无效或已过期，请使用最近收到的邮件重试。 / This unsubscribe link is invalid or expired. Please use a recent email.");
    }

}
