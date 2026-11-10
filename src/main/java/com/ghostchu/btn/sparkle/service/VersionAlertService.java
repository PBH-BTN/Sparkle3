package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertDelivery;
import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertMatch;
import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertRule;
import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertRuleInput;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.vdurmont.semver4j.Requirement;
import com.vdurmont.semver4j.Semver;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class VersionAlertService {
    private static final String RULES_KEY = "sparkle:admin:version-alerts:rules";
    private static final String INITIALIZED_KEY = "sparkle:admin:version-alerts:initialized";
    private static final String DELIVERY_KEY = "sparkle:admin:version-alerts:deliveries";
    private static final String SENT_KEY_PREFIX = "sparkle:admin:version-alerts:sent:";
    private static final String LEGACY_RULE_ID = "legacy-pbh-9.3.0-security";
    private static final String LEGACY_SENT_KEY_PREFIX = "pbh-930-security-alert:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final int MAX_DELIVERIES = 1000;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final IUserService userService;
    private final String fromAddress;
    private final String fromName;

    private volatile List<VersionAlertRule> cachedRules;
    private volatile long cacheExpiresAt;

    public VersionAlertService(
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            JavaMailSender mailSender,
            IUserService userService,
            @Value("${sparkle.version-alert.from-address}") String fromAddress,
            @Value("${sparkle.version-alert.from-name}") String fromName
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.userService = userService;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    public @NotNull List<VersionAlertRule> listRules() {
        return loadRules().stream()
                .sorted(Comparator.comparing(VersionAlertRule::updatedAt).reversed())
                .toList();
    }

    public @NotNull VersionAlertRule create(@NotNull VersionAlertRuleInput input, @Nullable Long operatorId)
            throws BusinessException {
        VersionAlertRuleInput normalized = validate(input);
        OffsetDateTime now = OffsetDateTime.now();
        VersionAlertRule rule = new VersionAlertRule(
                UUID.randomUUID().toString(),
                normalized.name().trim(),
                normalized.enabled(),
                normalized.product().trim(),
                normalized.versionRequirement().trim(),
                normalizeSeverity(normalized.severity()),
                normalized.subject().trim(),
                normalized.body(),
                normalized.oncePerUser(),
                now,
                now,
                operatorId
        );
        saveRule(rule);
        invalidateCache();
        return rule;
    }

    public @NotNull VersionAlertRule update(String id,
                                            @NotNull VersionAlertRuleInput input,
                                            @Nullable Long operatorId) throws BusinessException {
        VersionAlertRule existing = requireRule(id);
        VersionAlertRuleInput normalized = validate(input);
        VersionAlertRule updated = new VersionAlertRule(
                existing.id(),
                normalized.name().trim(),
                normalized.enabled(),
                normalized.product().trim(),
                normalized.versionRequirement().trim(),
                normalizeSeverity(normalized.severity()),
                normalized.subject().trim(),
                normalized.body(),
                normalized.oncePerUser(),
                existing.createdAt(),
                OffsetDateTime.now(),
                operatorId
        );
        saveRule(updated);
        invalidateCache();
        return updated;
    }

    public void delete(String id) {
        redisTemplate.opsForHash().delete(RULES_KEY, id);
        invalidateCache();
    }

    public @NotNull VersionAlertMatch match(String id, String userAgent) throws BusinessException {
        VersionAlertRule rule = requireRule(id);
        MatchedVersion matched = extractVersion(rule.product(), userAgent);
        return new VersionAlertMatch(
                matched != null && satisfies(matched.version(), rule.versionRequirement()),
                rule.product(),
                matched == null ? null : matched.version(),
                rule.versionRequirement(),
                userAgent
        );
    }

    public @NotNull VersionAlertDelivery sendTest(
            String id,
            String recipient,
            String sampleUserAgent,
            Long operatorId
    ) throws BusinessException {
        VersionAlertRule rule = requireRule(id);
        if (recipient == null || recipient.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A valid test recipient is required");
        }
        try {
            InternetAddress address = new InternetAddress(recipient.trim(), true);
            address.validate();
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A valid test recipient is required");
        }
        VersionAlertMatch match = match(id, sampleUserAgent);
        if (!match.matched()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "The sample User-Agent does not match this alert rule"
            );
        }
        return deliver(
                rule,
                null,
                recipient.trim(),
                "Administrator",
                match.version(),
                sampleUserAgent,
                "TEST",
                operatorId
        );
    }

    public void evaluateAsync(@Nullable Long ownerId, @Nullable String userAgent) {
        if (ownerId == null || userAgent == null || userAgent.isBlank()) {
            return;
        }
        List<VersionAlertRule> rules = loadRules().stream().filter(VersionAlertRule::enabled).toList();
        if (rules.isEmpty()) {
            return;
        }
        List<RuleMatch> matches = new ArrayList<>();
        for (VersionAlertRule rule : rules) {
            MatchedVersion matched = extractVersion(rule.product(), userAgent);
            if (matched != null && satisfies(matched.version(), rule.versionRequirement())) {
                matches.add(new RuleMatch(rule, matched.version()));
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        Thread.startVirtualThread(() -> {
            User user = userService.getById(ownerId);
            if (user == null
                    || user.getBannedAt() != null
                    || user.getEmail() == null
                    || user.getEmail().isBlank()
                    || user.getEmail().toLowerCase(Locale.ROOT).endsWith("@sparkle.system")) {
                return;
            }
            for (RuleMatch match : matches) {
                evaluateAndSend(match.rule(), user, match.version(), userAgent);
            }
        });
    }

    public @NotNull List<VersionAlertDelivery> deliveries(int limit) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        var values = redisTemplate.opsForZSet().reverseRange(DELIVERY_KEY, 0, safeLimit - 1L);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<VersionAlertDelivery> deliveries = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                deliveries.add(objectMapper.readValue(value, VersionAlertDelivery.class));
            } catch (Exception exception) {
                log.debug("Skipping malformed version alert delivery record: {}", exception.getMessage());
            }
        }
        return deliveries;
    }

    private void evaluateAndSend(VersionAlertRule rule,
                                 User user,
                                 String version,
                                 String userAgent) {
        String sentKey = SENT_KEY_PREFIX + rule.id() + ":" + user.getId();
        if (LEGACY_RULE_ID.equals(rule.id())
                && Boolean.TRUE.equals(redisTemplate.hasKey(LEGACY_SENT_KEY_PREFIX + user.getId()))) {
            redisTemplate.opsForValue().set(sentKey, String.valueOf(System.currentTimeMillis()));
            return;
        }
        if (rule.oncePerUser()) {
            Boolean reserved = redisTemplate.opsForValue().setIfAbsent(
                    sentKey,
                    String.valueOf(System.currentTimeMillis())
            );
            if (!Boolean.TRUE.equals(reserved)) {
                return;
            }
        } else {
            Boolean reserved = redisTemplate.opsForValue().setIfAbsent(
                    sentKey,
                    String.valueOf(System.currentTimeMillis()),
                    Duration.ofHours(24)
            );
            if (!Boolean.TRUE.equals(reserved)) {
                return;
            }
        }
        try {
            deliver(
                    rule,
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    version,
                    userAgent,
                    "AUTOMATIC",
                    null
            );
        } catch (BusinessException exception) {
            redisTemplate.delete(sentKey);
            log.warn("Version alert email failed for user {}: {}", user.getId(), exception.getMessage());
        }
    }

    private VersionAlertDelivery deliver(VersionAlertRule rule,
                                         Long userId,
                                         String to,
                                         String nickname,
                                         String version,
                                         String userAgent,
                                         String trigger,
                                         Long operatorId) throws BusinessException {
        OffsetDateTime sentAt = OffsetDateTime.now();
        VersionAlertDelivery delivery = new VersionAlertDelivery(
                UUID.randomUUID().toString(),
                rule.id(),
                rule.name(),
                userId,
                to,
                nickname,
                rule.product(),
                version,
                truncate(userAgent, 500),
                "SENT",
                null,
                trigger,
                sentAt
        );
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(to);
            helper.setSubject(render(rule.subject(), rule, nickname, version, userAgent, operatorId));
            helper.setText(render(rule.body(), rule, nickname, version, userAgent, operatorId), false);
            mailSender.send(mimeMessage);
            recordDelivery(delivery);
            return delivery;
        } catch (Exception exception) {
            VersionAlertDelivery failed = new VersionAlertDelivery(
                    delivery.id(),
                    delivery.ruleId(),
                    delivery.ruleName(),
                    delivery.userId(),
                    delivery.email(),
                    delivery.nickname(),
                    delivery.product(),
                    delivery.version(),
                    delivery.userAgent(),
                    "FAILED",
                    exception.getMessage(),
                    delivery.trigger(),
                    delivery.sentAt()
            );
            recordDelivery(failed);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Unable to send alert email: " + exception.getMessage());
        }
    }

    private void recordDelivery(VersionAlertDelivery delivery) {
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForZSet().add(
                    DELIVERY_KEY,
                    objectMapper.writeValueAsString(delivery),
                    now
            );
            redisTemplate.opsForZSet().removeRangeByScore(
                    DELIVERY_KEY,
                    0,
                    now - Duration.ofDays(90).toMillis()
            );
            redisTemplate.opsForZSet().removeRange(DELIVERY_KEY, 0, -MAX_DELIVERIES - 1L);
        } catch (Exception exception) {
            log.warn("Unable to record version alert delivery: {}", exception.getMessage());
        }
    }

    private String render(String template,
                          VersionAlertRule rule,
                          String nickname,
                          String version,
                          String userAgent,
                          Long operatorId) {
        return template
                .replace("{{nickname}}", safe(nickname))
                .replace("{{product}}", safe(rule.product()))
                .replace("{{version}}", safe(version))
                .replace("{{requirement}}", safe(rule.versionRequirement()))
                .replace("{{userAgent}}", safe(userAgent))
                .replace("{{ruleName}}", safe(rule.name()))
                .replace("{{operatorId}}", operatorId == null ? "" : operatorId.toString());
    }

    private VersionAlertRuleInput validate(VersionAlertRuleInput input) throws BusinessException {
        if (input.name() == null || input.name().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Alert name is required");
        }
        if (input.name().trim().length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Alert name is too long");
        }
        if (input.product() == null || input.product().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Client product name is required");
        }
        if (input.product().trim().length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Client product name is too long");
        }
        if (input.versionRequirement() == null || input.versionRequirement().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Version requirement is required");
        }
        if (input.versionRequirement().trim().length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Version requirement is too long");
        }
        try {
            Requirement.buildNPM(input.versionRequirement().trim()).isSatisfiedBy("1.0.0");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid semantic version requirement");
        }
        if (input.subject() == null || input.subject().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email subject is required");
        }
        if (input.subject().trim().length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email subject is too long");
        }
        if (input.body() == null || input.body().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email body is required");
        }
        if (input.body().length() > 50_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email body is too long");
        }
        return new VersionAlertRuleInput(
                input.name().trim(),
                input.enabled(),
                input.product().trim(),
                input.versionRequirement().trim(),
                normalizeSeverity(input.severity()),
                input.subject().trim(),
                input.body(),
                input.oncePerUser()
        );
    }

    private String normalizeSeverity(String severity) {
        if (severity == null) return "WARNING";
        String normalized = severity.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INFO", "WARNING", "SECURITY" -> normalized;
            default -> "WARNING";
        };
    }

    private MatchedVersion extractVersion(String product, String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        Pattern pattern = Pattern.compile(
                Pattern.quote(product) + "/([0-9]+(?:\\.[0-9]+){0,3}(?:[-+][0-9A-Za-z.-]+)?)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(userAgent);
        if (!matcher.find()) return null;
        try {
            return new MatchedVersion(
                    new Semver(matcher.group(1), Semver.SemverType.LOOSE).toString()
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean satisfies(String version, String requirement) {
        try {
            Semver semver = new Semver(version, Semver.SemverType.LOOSE);
            return semver.satisfies(Requirement.buildNPM(requirement));
        } catch (Exception exception) {
            return false;
        }
    }

    private List<VersionAlertRule> loadRules() {
        long now = System.currentTimeMillis();
        if (cachedRules != null && now < cacheExpiresAt) {
            return cachedRules;
        }
        synchronized (this) {
            if (cachedRules != null && now < cacheExpiresAt) {
                return cachedRules;
            }
            ensureInitialized();
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(RULES_KEY);
            List<VersionAlertRule> rules = new ArrayList<>(entries.size());
            for (Object value : entries.values()) {
                try {
                    rules.add(objectMapper.readValue(String.valueOf(value), VersionAlertRule.class));
                } catch (Exception exception) {
                    log.warn("Skipping malformed version alert rule: {}", exception.getMessage());
                }
            }
            cachedRules = List.copyOf(rules);
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return cachedRules;
        }
    }

    private void ensureInitialized() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(INITIALIZED_KEY))) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(INITIALIZED_KEY))) {
                return;
            }
            Boolean first = redisTemplate.opsForValue().setIfAbsent(INITIALIZED_KEY, "true");
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(RULES_KEY))) {
                OffsetDateTime now = OffsetDateTime.now();
                VersionAlertRule legacyAlert = new VersionAlertRule(
                        LEGACY_RULE_ID,
                        "PeerBanHelper 9.3.0 security alert",
                        true,
                        "PeerBanHelper",
                        "9.3.0",
                        "SECURITY",
                        "[SparkleBTN] 您使用的 PeerBanHelper (v{{version}}) 存在安全风险，建议您及时升级",
                        """
                                您好 {{nickname}}，
                                  您之所以收到这封邮件，是因为 SparkleBTN 系统已检测到您正在使用存在已知安全问题的 PeerBanHelper 版本 ({{version}})。
                                  作为对用户安全性负责的一部分，SparkleBTN 会向使用存在已知安全问题的注册用户发送安全通知邮件。不幸的是，您处于受影响的范围内。
                                  我们已发布紧急安全更新，并修复了相关安全漏洞，请您及时更新！您可以从下面的链接或者通过 PeerBanHelper 自带的更新程序进行更新：
                                  https://github.com/PBH-BTN/PeerBanHelper/releases/latest
                                  对您带来的不便，我们致以诚挚歉意。
                                  此邮件仅发送一次。
                                """,
                        true,
                        now,
                        now,
                        null
                );
                try {
                    saveRule(legacyAlert);
                } catch (BusinessException exception) {
                    throw new IllegalStateException("Unable to initialize legacy version alert", exception);
                }
            }
        }
    }

    private VersionAlertRule requireRule(String id) throws BusinessException {
        return loadRules().stream()
                .filter(rule -> rule.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Version alert rule not found"));
    }

    private void saveRule(VersionAlertRule rule) throws BusinessException {
        try {
            redisTemplate.opsForHash().put(RULES_KEY, rule.id(), objectMapper.writeValueAsString(rule));
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save version alert rule");
        }
    }

    private void invalidateCache() {
        cachedRules = null;
        cacheExpiresAt = 0;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record MatchedVersion(String version) {
    }

    private record RuleMatch(VersionAlertRule rule, String version) {
    }
}
