package com.ghostchu.btn.sparkle.service.btn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BtnConfigCatalog {
    public enum ValueType {
        BOOLEAN,
        INTEGER,
        DECIMAL,
        ENDPOINT,
        URL,
        ALGORITHM,
        TEXT,
        LIST,
        TIME_ZONE,
        EMAIL,
        SECRET,
        CRON
    }

    public record Definition(
            String section,
            String field,
            String key,
            ValueType type,
            String defaultValue,
            Long min,
            Long max,
            boolean sensitive
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
                    null,
                    false
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
                    null,
                    false
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
                    max,
                    false
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
                    null,
                    false
            );
        }

        public static Definition url(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.URL,
                    defaultValue,
                    null,
                    null,
                    false
            );
        }

        public static Definition algorithm(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.ALGORITHM,
                    defaultValue,
                    null,
                    null,
                    false
            );
        }

        public static Definition decimal(
                String section,
                String field,
                String defaultValue,
                long min,
                long max
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.DECIMAL,
                    defaultValue,
                    min,
                    max,
                    false
            );
        }

        public static Definition freeText(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.TEXT,
                    defaultValue,
                    null,
                    null,
                    false
            );
        }

        public static Definition list(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.LIST,
                    defaultValue,
                    null,
                    null,
                    false
            );
        }

        public static Definition timeZone(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.TIME_ZONE,
                    defaultValue,
                    null,
                    null,
                    false
            );
        }

        public static Definition email(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.EMAIL,
                    defaultValue,
                    null,
                    null,
                    false
            );
        }

        public static Definition secret(
                String section,
                String field,
                String defaultValue
        ) {
            return new Definition(
                    section,
                    field,
                    BtnConfigCatalog.key(section, field),
                    ValueType.SECRET,
                    defaultValue,
                    null,
                    null,
                    true
            );
        }
    }

    private static final List<Definition> DEFINITIONS = List.of(
            Definition.integer("protocol", "minProtocolVersion", 20, 1, 10_000),
            Definition.integer("protocol", "maxProtocolVersion", 20, 1, 10_000),

            Definition.integer("reconfigure", "interval", 3_600_000, 100, 86_400_000),
            Definition.integer("reconfigure", "randomInitialDelay", 600_000, 0, 86_400_000),

            Definition.text(
                    "syncBanHistory",
                    "endpoint",
                    "{rooturl}/ping/syncBanHistory"
            ),
            Definition.integer("syncBanHistory", "interval", 900_000, 100, 86_400_000),
            Definition.integer("syncBanHistory", "randomInitialDelay", 15_000, 0, 86_400_000),
            Definition.bool("syncBanHistory", "powCaptcha", false),

            Definition.text("syncSwarm", "endpoint", "{rooturl}/ping/syncSwarm"),
            Definition.integer("syncSwarm", "interval", 900_000, 100, 86_400_000),
            Definition.integer("syncSwarm", "randomInitialDelay", 15_000, 0, 86_400_000),
            Definition.bool("syncSwarm", "powCaptcha", false),
            Definition.integer(
                    "syncSwarm",
                    "dataRetentionTime",
                    259_200_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.cron("syncSwarm", "dataRetentionCron", "0 0 */2 * * *"),

            Definition.text(
                    "ruleIpDenylist",
                    "endpoint",
                    "{rooturl}/ping/ruleIpDenylist"
            ),
            Definition.integer("ruleIpDenylist", "interval", 600_000, 100, 86_400_000),
            Definition.integer("ruleIpDenylist", "randomInitialDelay", 15_000, 0, 86_400_000),
            Definition.bool("ruleIpDenylist", "powCaptcha", false),

            Definition.text(
                    "ruleIpAllowlist",
                    "endpoint",
                    "{rooturl}/ping/ruleIpAllowlist"
            ),
            Definition.integer("ruleIpAllowlist", "interval", 600_000, 100, 86_400_000),
            Definition.integer("ruleIpAllowlist", "randomInitialDelay", 15_000, 0, 86_400_000),
            Definition.bool("ruleIpAllowlist", "powCaptcha", false),

            Definition.text(
                    "rulePeerIdentity",
                    "endpoint",
                    "{rooturl}/ping/rulePeerIdentity"
            ),
            Definition.integer("rulePeerIdentity", "interval", 2_700_000, 100, 86_400_000),
            Definition.integer("rulePeerIdentity", "randomInitialDelay", 15_000, 0, 86_400_000),
            Definition.bool("rulePeerIdentity", "powCaptcha", false),

            Definition.text("heartbeat", "endpoint", "{rooturl}/ping/heartbeat"),
            Definition.integer("heartbeat", "interval", 1_800_000, 100, 86_400_000),
            Definition.integer("heartbeat", "randomInitialDelay", 3_000, 0, 86_400_000),
            Definition.bool("heartbeat", "powCaptcha", false),
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
            Definition.bool("queryIp", "powCaptcha", false),

            Definition.url("rootUrls", "rootUrl", "https://sparkle.pbh-btn.com"),
            Definition.url("rootUrls", "chnRootUrl", "https://sparkle.ghostchu.com"),

            Definition.text(
                    "captchaPow",
                    "endpoint",
                    "{rooturl}/ping/captcha/createSession"
            ),
            Definition.integer("captchaPow", "difficultyBits", 6, 1, 64),
            Definition.algorithm("captchaPow", "algorithm", "SHA-256"),

            Definition.list(
                    "queryIpStatistics",
                    "includeModules",
                    "com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker,"
                            + "com.ghostchu.peerbanhelper.module.impl.rule.MultiDialingBlocker,"
                            + "com.ghostchu.peerbanhelper.module.impl.rule.AutoRangeBan,"
                            + "com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline"
            ),
            Definition.integer(
                    "queryIpStatistics",
                    "bansCountingDuration",
                    604_800_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.integer(
                    "queryIpStatistics",
                    "swarmsCountingDuration",
                    604_800_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.integer(
                    "queryIpStatistics",
                    "heartbeatQueryDuration",
                    604_800_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.integer(
                    "queryIpStatistics",
                    "trafficMeasureDuration",
                    604_800_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.integer(
                    "queryIpStatistics",
                    "torrentsCountingDuration",
                    604_800_000,
                    60_000,
                    31_536_000_000L
            ),

            Definition.decimal(
                    "rankingWeights",
                    "sentTrafficOtherAck",
                    "20",
                    0,
                    1_000_000
            ),
            Definition.decimal(
                    "rankingWeights",
                    "sentTrafficSelfReport",
                    "10",
                    0,
                    1_000_000
            ),
            Definition.decimal(
                    "rankingWeights",
                    "receivedTrafficOtherAck",
                    "5",
                    0,
                    1_000_000
            ),
            Definition.decimal(
                    "rankingWeights",
                    "receivedTrafficSelfReport",
                    "1",
                    0,
                    1_000_000
            ),

            Definition.integer(
                    "userIpWarning",
                    "duration",
                    1_209_600_000,
                    60_000,
                    31_536_000_000L
            ),
            Definition.cron("userIpWarning", "cron", "-"),

            Definition.bool("measure", "enable", false),
            Definition.secret("measure", "globalpingToken", "foobar"),
            Definition.cron(
                    "measure",
                    "globalpingResultRetrieveCron",
                    "0 */5 * * * *"
            ),
            Definition.cron(
                    "measure",
                    "globalpingStartMeasureCron",
                    "0 49 * * * *"
            ),
            Definition.integer(
                    "measure",
                    "expiredAfter",
                    31_104_000_000L,
                    60_000,
                    315_360_000_000L
            ),
            Definition.integer("measurePrefix", "ipv4", 24, 0, 32),
            Definition.integer("measurePrefix", "ipv6", 48, 0, 128),

            Definition.integer(
                    "security",
                    "zipBombThreshold",
                    33_554_432,
                    1_024,
                    1_073_741_824
            ),

            Definition.bool("userappAutoAccount", "allowRegister", true),
            Definition.bool("userappAutoAccount", "allowLogin", true),
            Definition.integer(
                    "userappAutoAccount",
                    "holderUid",
                    16_402,
                    1,
                    9_007_199_254_740_991L
            ),

            Definition.cron(
                    "banHistory",
                    "refreshModuleNameCacheCron",
                    "0 0 */2 * * *"
            ),
            Definition.timeZone("points", "timeZone", "Asia/Shanghai"),

            Definition.email("versionAlert", "fromAddress", "sparkle@pbh-btn.com"),
            Definition.freeText("versionAlert", "fromName", "SparkleBTN 系统通知"),

            Definition.email("userContactEmail", "fromAddress", "sparkle@pbh-btn.com"),
            Definition.freeText("userContactEmail", "fromName", "Sparkle BTN 用户邮件"),

            Definition.freeText("userAvatar", "cacheDirectory", "data/avatar-cache"),
            Definition.integer(
                    "userAvatar",
                    "cacheDuration",
                    2_592_000_000L,
                    60_000,
                    31_536_000_000L
            ),
            Definition.cron("userAvatar", "cleanupCron", "0 30 4 * * *")
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
            case "rootUrls" -> "sparkle";
            case "captchaPow" -> "sparkle.captcha.pow";
            case "queryIpStatistics" -> "sparkle.query.query-ip";
            case "rankingWeights" -> "sparkle.ranking.weight.user-swarm-statistics";
            case "userIpWarning" -> "sparkle.admin.user-ip-warning";
            case "measure" -> "sparkle.measure";
            case "measurePrefix" -> "sparkle.measure.prefix-length";
            case "security" -> "sparkle.security";
            case "userappAutoAccount" -> "sparkle.userapp.auto-account";
            case "banHistory" -> "sparkle.banhistory";
            case "points" -> "sparkle.points";
            case "versionAlert" -> "sparkle.version-alert";
            case "userContactEmail" -> "sparkle.user-contact-email";
            case "userAvatar" -> "sparkle.user-avatar";
            default -> throw new IllegalArgumentException("Unknown BTN config section: " + section);
        };
        return prefix + "." + fieldToKey(field);
    }

    private static String fieldToKey(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
