package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewEntry;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class BtnBypassConflictAlertService {
    private static final String MODULE_ID = "btnbypass-conflict-alert";
    private static final String SUBJECT = "BTN Bypass 预警：应用 IP 命中封禁规则 / BTN Bypass IP rule alert";
    private static final TypeReference<Map<String, Set<Long>>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final AnalyseModuleConfigService config;
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper json;
    private final RulePreviewService rules;
    private final IUserappService apps;
    private final IUserService users;
    private final UserMessageService messages;
    private final JdbcTemplate jdbc;

    public BtnBypassConflictAlertService(
            AnalyseModuleConfigService config,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redis,
            ObjectMapper json,
            RulePreviewService rules,
            IUserappService apps,
            IUserService users,
            UserMessageService messages,
            JdbcTemplate jdbc
    ) {
        this.config = config;
        this.redis = redis;
        this.json = json;
        this.rules = rules;
        this.apps = apps;
        this.users = users;
        this.messages = messages;
        this.jdbc = jdbc;
    }

    @Transactional
    public void scan() {
        List<Long> configuredUids = AnalyseModuleConfigService.recipientUids(
                config.textValue(MODULE_ID, "recipient-uids", ""));
        if (configuredUids.isEmpty()) {
            MonitoredTaskOutcome.skipped("No alert recipients configured");
            return;
        }

        // Serialize scans across instances. Inbox inserts and checkpoint changes commit together.
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended('sparkle:btn-bypass-conflict-alert', 0))",
                Boolean.class))) {
            MonitoredTaskOutcome.skipped("Another instance is scanning");
            return;
        }

        boolean bypassEnabled = config.isEnabled("btnbypass-userapps");
        var snapshot = bypassEnabled ? readSnapshot() : Map.<String, Set<Long>>of();
        if (snapshot == null) {
            MonitoredTaskOutcome.skipped("Waiting for the Bypass allowlist to refresh");
            return;
        }
        List<Long> recipients = users.listByIds(configuredUids).stream()
                .filter(AdminPolicy::hasModeratorAccess)
                .map(User::getId)
                .toList();
        Map<Long, Set<Match>> matches = recipients.isEmpty() || snapshot.isEmpty()
                ? Map.of() : match(snapshot, rules.ipDenyEntries(true, true));
        var delivered = new HashSet<>(jdbc.query("""
                SELECT recipient_id, application_id, match_fingerprint FROM btn_bypass_conflict_alerts
                """, (rs, row) -> new Checkpoint(rs.getLong(1), rs.getLong(2), rs.getString(3))));
        Set<Checkpoint> inserted = new HashSet<>();
        var observedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<Userapp> applications = matches.isEmpty() ? List.of() : apps.listByIds(matches.keySet());
        Map<Long, User> owners = applications.isEmpty() ? Map.of() : users.listByIds(applications.stream()
                        .map(Userapp::getOwner).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        for (Userapp app : applications) {
            User owner = owners.get(app.getOwner());
            if (owner == null || !Boolean.TRUE.equals(app.getBtnBypass())
                    || app.getDeleteAt() != null || app.isSuspended()) {
                continue;
            }
            for (long recipient : recipients) {
                List<Match> newMatches = matches.get(app.getId()).stream()
                        .filter(hit -> !delivered.remove(new Checkpoint(recipient, app.getId(), hit.fingerprint())))
                        .toList();
                if (newMatches.isEmpty()) {
                    continue;
                }
                send(recipient, app, owner, newMatches, observedAt);
                for (Match hit : newMatches) {
                    inserted.add(new Checkpoint(recipient, app.getId(), hit.fingerprint()));
                }
            }
        }
        if (!delivered.isEmpty()) {
            jdbc.batchUpdate("""
                    DELETE FROM btn_bypass_conflict_alerts
                    WHERE recipient_id = ? AND application_id = ? AND match_fingerprint = ?
                    """, delivered, 500, Checkpoint::bind);
        }
        if (!inserted.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO btn_bypass_conflict_alerts (recipient_id, application_id, match_fingerprint)
                    VALUES (?, ?, ?)
                    """, inserted, 500, Checkpoint::bind);
        } else {
            MonitoredTaskOutcome.skipped("No new BTN Bypass conflicts to deliver");
        }
    }

    private Map<String, Set<Long>> readSnapshot() {
        String data = redis.opsForValue().get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_OWNERS.getKey());
        if (data == null) {
            return null;
        }
        try {
            return json.readValue(data, SNAPSHOT_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read BTN Bypass application associations");
        }
    }

    static Map<Long, Set<Match>> match(Map<String, Set<Long>> snapshot, List<RulePreviewEntry> rules) {
        Map<IPAddress.IPVersion, TreeMap<BigInteger, Set<Long>>> addresses = new EnumMap<>(IPAddress.IPVersion.class);
        snapshot.forEach((ip, applicationIds) -> {
            IPAddress address = IpRuleRange.parseAddress(ip);
            if (address != null && !address.isMultiple()) {
                addresses.computeIfAbsent(address.getIPVersion(), ignored -> new TreeMap<>())
                        .computeIfAbsent(address.getValue(), ignored -> new HashSet<>()).addAll(applicationIds);
            }
        });

        Map<Long, Set<Match>> result = new LinkedHashMap<>();
        for (RulePreviewEntry entry : rules) {
            if (entry.content() == null) {
                continue;
            }
            entry.content().lines().forEach(line -> {
                IpRuleRange rule = IpRuleRange.parse(line);
                if (rule == null || !addresses.containsKey(rule.version())) {
                    return;
                }
                addresses.get(rule.version()).subMap(rule.lower(), true, rule.upper(), true)
                        .forEach((address, applicationIds) -> {
                            String fingerprint = Hashing.sha256().hashString(
                                    rule.version() + ":" + address + ":" + rule.lower() + ":" + rule.upper()
                                            + "\n" + entry.source() + "\n" + Objects.toString(entry.category(), ""),
                                    StandardCharsets.UTF_8).toString();
                            var hit = new Match(fingerprint, rule.version() + ":" + address, ruleSource(entry));
                            for (long appId : applicationIds) {
                                result.computeIfAbsent(appId, ignored -> new HashSet<>()).add(hit);
                            }
                        });
            });
        }
        return result;
    }

    private static String ruleSource(RulePreviewEntry entry) {
        String name = IpQueryLabelService.Label.ruleName(entry.source());
        String label = name == null ? "自动封禁规则 / Automated IP rules" : name;
        return entry.category() == null || entry.category().isBlank()
                ? label : label + " · " + entry.category().replaceAll("\\p{Cntrl}", " ");
    }

    private void send(long recipient, Userapp app, User owner, List<Match> matches, OffsetDateTime observedAt) {
        String sources = matches.stream().map(Match::source).distinct().sorted().limit(10)
                .map(source -> source.substring(0, Math.min(160, source.length())))
                .collect(Collectors.joining("、"));
        long addressCount = matches.stream().map(Match::address).distinct().count();
        String body = """
                此 BTN Bypass 应用的 IP 命中了 IP 封禁规则。请核查是否存在潜在违规，或是否由共享 IP 引起。
                An IP associated with this BTN Bypass application matched a deny rule. Please review for possible misuse or a shared IP.

                AppId：%s
                应用主键 ID / Application record ID：%d
                用户名 / Username：%s
                用户 UID / User UID：%d
                用户联系邮箱 / Contact email：%s
                新命中 IP 数 / IPs with new matches：%d
                规则来源（最多列出 10 项）/ Rule sources (up to 10)：%s
                发现时间 / Detected at：%s
                """.formatted(app.getAppId(), app.getId(), owner.getNickname(), owner.getId(),
                Objects.requireNonNullElse(owner.getEmail(), "未填写 / Unavailable"), addressCount, sources, observedAt);
        try {
            if (!messages.publishSystemToUser(recipient, NotificationCategory.ACCOUNT_SECURITY, SUBJECT, body, false)) {
                throw new IllegalStateException("BTN Bypass alert was not delivered");
            }
        } catch (BusinessException exception) {
            throw new IllegalStateException("Unable to deliver BTN Bypass alert");
        }
    }

    record Match(String fingerprint, String address, String source) {}

    private record Checkpoint(long recipientId, long applicationId, String fingerprint) {
        static void bind(java.sql.PreparedStatement statement, Checkpoint checkpoint) throws java.sql.SQLException {
            statement.setLong(1, checkpoint.recipientId());
            statement.setLong(2, checkpoint.applicationId());
            statement.setString(3, checkpoint.fingerprint());
        }
    }
}
