package com.ghostchu.btn.sparkle.service.btn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BtnConfigCatalog {
    public enum ValueType {
        BOOLEAN,
        INTEGER,
        ENDPOINT,
        CRON
    }

    public record Definition(
            String section,
            String field,
            String key,
            ValueType type,
            String defaultValue,
            Long min,
            Long max
    ) {
        public static Definition text(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.ENDPOINT,
                    defaultValue,
                    null,
                    null
            );
        }

        public static Definition bool(
                String section,
                String field,
                boolean defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.BOOLEAN,
                    Boolean.toString(defaultValue),
                    null,
                    null
            );
        }

        public static Definition integer(
                String section,
                String field,
                long defaultValue,
                long min,
                long max
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.INTEGER,
                    Long.toString(defaultValue),
                    min,
                    max
            );
        }

        public static Definition cron(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.CRON,
                    defaultValue,
                    null,
                    null
            );
        }
    }

    private static final List<Definition> DEFINITIONS = List.of(
            Definition.integer("protocol", "minProtocolVersion", 20, 1, 10_000),
            Definition.integer("protocol", "maxProtocolVersion", 20, 1, 10_000),

            Definition.integer("reconfigure", "interval", 10_800_000, 100, 86_400_000),
            Definition.integer("reconfigure", "randomInitialDelay", 600_000, 0, 86_400_000),

            Definition.text(
                    "syncBanHistory",
                    "endpoint",
                    "{rooturl}/ping/syncBanHistory"
            ),
            Definition.integer("syncBanHistory", "interval", 10_000, 100, 86_400_000),
            Definition.integer("syncBanHistory", "randomInitialDelay", 3_000, 0, 86_400_000),
            Definition.bool("syncBanHistory", "powCaptcha", false),

            Definition.text("syncSwarm", "endpoint", "{rooturl}/ping/syncSwarm"),
            Definition.integer("syncSwarm", "interval", 10_000, 100, 86_400_000),
            Definition.integer("syncSwarm", "randomInitialDelay", 3_000, 0, 86_400_000),
            Definition.bool("syncSwarm", "powCaptcha", false),
            Definition.integer(
                    "syncSwarm",
                    "dataRetentionTime",
                    604_800_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.cron("syncSwarm", "dataRetentionCron", "0 10 * * * *"),

            Definition.text(
                    "ruleIpDenylist",
                    "endpoint",
                    "{rooturl}/ping/ruleIpDenylist"
            ),
            Definition.integer("ruleIpDenylist", "interval", 10_000, 100, 86_400_000),
            Definition.integer("ruleIpDenylist", "randomInitialDelay", 3_000, 0, 86_400_000),
            Definition.bool("ruleIpDenylist", "powCaptcha", false),

            Definition.text(
                    "ruleIpAllowlist",
                    "endpoint",
                    "{rooturl}/ping/ruleIpAllowlist"
            ),
            Definition.integer("ruleIpAllowlist", "interval", 10_000, 100, 86_400_000),
            Definition.integer("ruleIpAllowlist", "randomInitialDelay", 3_000, 0, 86_400_000),
            Definition.bool("ruleIpAllowlist", "powCaptcha", false),

            Definition.text(
                    "rulePeerIdentity",
                    "endpoint",
                    "{rooturl}/ping/rulePeerIdentity"
            ),
            Definition.integer("rulePeerIdentity", "interval", 10_000, 100, 86_400_000),
            Definition.integer("rulePeerIdentity", "randomInitialDelay", 3_000, 0, 86_400_000),
            Definition.bool("rulePeerIdentity", "powCaptcha", false),

            Definition.text("heartbeat", "endpoint", "{rooturl}/ping/heartbeat"),
            Definition.integer("heartbeat", "interval", 10_000, 100, 86_400_000),
            Definition.integer("heartbeat", "randomInitialDelay", 3_000, 0, 86_400_000),
            Definition.bool("heartbeat", "powCaptcha", true),
            Definition.bool("heartbeat", "multiIf", true),
            Definition.integer(
                    "heartbeat",
                    "cleanupBefore",
                    604_800_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.cron("heartbeat", "cleanupCron", "0 0 0 * * *"),

            Definition.integer("heartbeatQueue", "flushIntervalMs", 1_000, 100, 60_000),
            Definition.integer("heartbeatQueue", "initialDelayMs", 1_000, 0, 600_000),
            Definition.integer("heartbeatQueue", "batchSize", 2_000, 1, 100_000),
            Definition.integer("heartbeatQueue", "maxSize", 100_000, 1, 10_000_000),

            Definition.text("queryIp", "endpoint", "{rooturl}/ping/queryIp"),
            Definition.text(
                    "queryIp",
                    "iframeEndpoint",
                    "{rooturl}/ping/queryIp/widget"
            ),
            Definition.bool("queryIp", "powCaptcha", true)
    );

    private static final Map<String, Definition> BY_KEY;
    private static final List<String> KEYS;

    static {
        Map<String, Definition> byKey = new LinkedHashMap<>();
        for (Definition definition : DEFINITIONS) {
            Definition previous = byKey.put(definition.key(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate BTN config key: " + definition.key());
            }
        }
        BY_KEY = Map.copyOf(byKey);
        KEYS = List.copyOf(byKey.keySet());
    }

    private BtnConfigCatalog() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static List<String> keys() {
        return KEYS;
    }

    public static Definition definition(String key) {
        return BY_KEY.get(key);
    }

    public static String key(String section, String field) {
        String prefix = switch (section) {
            case "protocol" -> "sparkle.ping.config";
            case "reconfigure" -> "sparkle.ping.reconfigure";
            case "syncBanHistory" -> "sparkle.ping.sync-banhistory";
            case "syncSwarm" -> "sparkle.ping.sync-swarm";
            case "ruleIpDenylist" -> "sparkle.ping.rule-ip-denylist";
            case "ruleIpAllowlist" -> "sparkle.ping.rule-ip-allowlist";
            case "rulePeerIdentity" -> "sparkle.ping.rule-peer-identity";
            case "heartbeat" -> "sparkle.ping.heartbeat";
            case "heartbeatQueue" -> "sparkle.ping.heartbeat.queue";
            case "queryIp" -> "sparkle.ping.query-ip";
            default -> throw new IllegalArgumentException("Unknown BTN config section: " + section);
        };
        return prefix + "." + fieldToKey(field);
    }

    private static String fieldToKey(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
