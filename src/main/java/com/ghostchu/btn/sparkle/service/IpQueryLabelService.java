package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
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
        int comment = rawLine.indexOf('#');
        String line = (comment < 0 ? rawLine : rawLine.substring(0, comment)).strip();
        if (line.isEmpty()) {
            return false;
        }
        int comma = line.indexOf(',');
        if (comma >= 0) {
            String[] fields = line.split(",", 3);
            try {
                if (fields.length < 2 || Integer.parseInt(fields[1].strip()) >= 128) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
            line = fields[0].strip();
        }

        IPAddress first = parseAddress(line);
        if (first != null) {
            return overlaps(network, first, first);
        }
        int dash = line.indexOf('-');
        return dash >= 0 && overlaps(network,
                parseAddress(line.substring(0, dash)), parseAddress(line.substring(dash + 1)));
    }

    private static IPAddress parseAddress(String value) {
        IPAddress address = new IPAddressString(value.strip()).getAddress();
        if (address != null && address.isIPv4Convertible()) {
            address = address.toIPv4();
        }
        return address != null && address.getPrefixLength() != null ? address.toPrefixBlock() : address;
    }

    private static boolean overlaps(IPAddress network, IPAddress first, IPAddress last) {
        return first != null && last != null
                && network.getIPVersion() == first.getIPVersion()
                && network.getIPVersion() == last.getIPVersion()
                && network.getLower().getValue().compareTo(last.getUpper().getValue()) <= 0
                && network.getUpper().getValue().compareTo(first.getLower().getValue()) >= 0;
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

        private static String ruleName(String source) {
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
