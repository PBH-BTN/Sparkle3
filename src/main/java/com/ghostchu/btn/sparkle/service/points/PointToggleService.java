package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PointToggleService {
    public static final String SETTINGS_MODULE_ID = "points-settings";

    private static final Map<PointCategory, String> FIELDS;

    static {
        Map<PointCategory, String> fields = new EnumMap<>(PointCategory.class);
        fields.put(PointCategory.LOGIN, "login-enabled");
        fields.put(PointCategory.ACTIVE_APP, "active-app-enabled");
        fields.put(PointCategory.APP_ONLINE, "app-online-enabled");
        fields.put(PointCategory.BAN_HISTORY, "ban-history-enabled");
        fields.put(PointCategory.SWARM_TRACKER, "swarm-tracker-enabled");
        fields.put(PointCategory.RULES_FETCH, "rules-fetch-enabled");
        fields.put(PointCategory.KEEPALIVE, "keepalive-enabled");
        fields.put(PointCategory.RULE_HIT_IP, "rule-hit-ip-enabled");
        fields.put(PointCategory.NEW_IP, "new-ip-enabled");
        fields.put(PointCategory.WASTED_UPLOAD, "wasted-upload-enabled");
        fields.put(PointCategory.NEW_TORRENT, "new-torrent-enabled");
        fields.put(PointCategory.CLIENT_DISCOVERY, "client-discovery-enabled");
        fields.put(PointCategory.DYNAMIC_SEED_UPLOAD, "dynamic-seed-upload-enabled");
        fields.put(PointCategory.DYNAMIC_DOWNLOAD, "dynamic-download-enabled");
        fields.put(PointCategory.DYNAMIC_HOT_PEER, "dynamic-hot-peer-enabled");
        FIELDS = Map.copyOf(fields);
    }

    private final AnalyseModuleConfigService moduleConfigService;

    public boolean isEnabled(PointCategory category) {
        String field = FIELDS.get(category);
        return field != null
                && moduleConfigService.booleanValue(SETTINGS_MODULE_ID, field, true);
    }

    public boolean isAnyDynamicEnabled() {
        return isEnabled(PointCategory.DYNAMIC_SEED_UPLOAD)
                || isEnabled(PointCategory.DYNAMIC_DOWNLOAD)
                || isEnabled(PointCategory.DYNAMIC_HOT_PEER);
    }

    public boolean isAnyTriggeredEnabled() {
        return java.util.Arrays.stream(PointCategory.values())
                .filter(category -> category.getComponent()
                        != com.ghostchu.btn.sparkle.constants.PointComponent.DYNAMIC)
                .anyMatch(this::isEnabled);
    }
}
