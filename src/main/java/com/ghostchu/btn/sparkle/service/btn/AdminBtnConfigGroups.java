package com.ghostchu.btn.sparkle.service.btn;

import java.util.List;

/** Every legacy BTN field has exactly one admin editor. Configuration keys stay unchanged. */
public final class AdminBtnConfigGroups {
    public static final List<String> GROUPS = List.of("communication", "reporting", "rule-delivery",
            "reporting-risk", "query", "ranking", "ip-warning", "measure", "security",
            "auto-accounts", "retention", "maintenance", "points-timezone", "mail-identities", "cache");

    private AdminBtnConfigGroups() {
    }

    public static String groupOf(BtnConfigCatalog.Definition definition) {
        String field = definition.field();
        return switch (definition.section()) {
            case "protocol", "reconfigure", "rootUrls" -> "communication";
            case "syncBanHistory", "syncSwarm", "heartbeat" -> {
                if (field.startsWith("volumeRisk") || field.startsWith("repeatBan")) {
                    yield "reporting-risk";
                }
                if (List.of("dataRetentionTime", "dataRetentionCron", "cleanupBefore", "cleanupCron").contains(field)) {
                    yield "retention";
                }
                yield "reporting";
            }
            case "banReplaySuspension" -> "reporting-risk";
            case "ruleIpDenylist", "ruleIpAllowlist", "rulePeerIdentity" -> "rule-delivery";
            case "queryIp", "queryIpStatistics" -> "query";
            case "rankingWeights" -> "ranking";
            case "userIpWarning" -> "ip-warning";
            case "measure", "measurePrefix" -> "measure";
            case "security", "captchaPow" -> "security";
            case "userappAutoAccount" -> "auto-accounts";
            case "banHistory" -> "maintenance";
            case "points" -> "points-timezone";
            case "versionAlert", "userContactEmail" -> "mail-identities";
            case "userAvatar" -> "cache";
            default -> throw new IllegalArgumentException("Unassigned admin configuration section");
        };
    }
}
