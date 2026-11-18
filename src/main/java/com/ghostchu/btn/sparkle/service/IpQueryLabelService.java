package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inet.ipaddr.IPAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IpQueryLabelService {
    private final RulePreviewService rulePreview;
    private final IUserappsHeartbeatService heartbeats;
    private final IUserappService apps;
    private final IUserService users;
    private final UserPublicProfileService profiles;

    public List<Label> labels(IPAddress network, OffsetDateTime heartbeatSince) {
        var labels = new LinkedHashSet<Label>();
        for (var section : rulePreview.ipSections(true)) {
            for (var entry : section.entries()) {
                if (entry.content() == null || entry.content().lines().noneMatch(line -> matches(network, line))) {
                    continue;
                }
                String source = Label.ruleName(entry.source()) == null ? null : entry.source();
                labels.add(new Label(section.type(), source, entry.category()));
                if ("btn-bypass".equals(entry.source())) {
                    labels.add(new Label("btn_bypass", null, null));
                }
            }
        }

        for (var heartbeat : heartbeats.fetchIpHeartbeatRecords(network.toCanonicalString(), heartbeatSince)) {
            var app = apps.getById(heartbeat.getUserappId());
            var user = app == null ? null : users.getById(app.getOwner());
            if (user != null) {
                String nickname = network.isMultiple() ? null : profiles.publicNickname(user);
                labels.add(new Label("btn_user", null, nickname));
                break;
            }
        }
        return List.copyOf(labels);
    }

    private static boolean matches(IPAddress network, String rawLine) {
        IpRuleRange range = IpRuleRange.parse(rawLine);
        return range != null && range.overlaps(network);
    }

    public record Label(String type, String source, String name) {
        @JsonIgnore
        public String text() {
            return switch (type) {
                case "btn_bypass" -> "BTN Bypass";
                case "btn_user" -> name == null || name.isBlank() ? "BTN用户" : "BTN用户->" + name;
                default -> ("ip_allowlist".equals(type) ? "IP 允许列表" : "IP 黑名单")
                        + "->" + (name == null || name.isBlank()
                        ? Objects.requireNonNullElse(ruleName(source), "动态生成规则") : name);
            };
        }

        static String ruleName(String source) {
            return switch (source) {
                case "manual" -> "手动规则";
                case "overdownload" -> "过量下载分析";
                case "untrusted-vote" -> "不可信投票分析";
                case "concurrent-download" -> "并发下载分析";
                case "random-identity" -> "随机身份分析";
                case "rain000-identity" -> "Rain 0.0.0 身份分析";
                case "gopeeddev-identity" -> "Gopeed dev 身份分析";
                case "datacenter-high-risk" -> "高风险数据中心分析";
                case "geoip-city-blacklist" -> "GeoIP 城市黑名单";
                case "mass-download-monitor" -> "大量下载监测模块";
                case "btn-bypass" -> "BTN Bypass 用户应用";
                case null, default -> null;
            };
        }
    }
}
