package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointRulesService {
    public static final String FIXED_RULES_MODULE_ID = "points-fixed-rules";
    public static final String ONCE_RULES_MODULE_ID = "points-once-rules";
    public static final String DYNAMIC_RULES_MODULE_ID = "points-dynamic-rules";

    private final AnalyseModuleConfigService configService;

    public long points(PointCategory category) {
        return switch (category) {
            case LOGIN -> fixed("login-points", 1_000L);
            case ACTIVE_APP -> fixed("active-app-points", 5_000L);
            case APP_ONLINE -> fixed("app-online-points", 500L);
            case BAN_HISTORY -> fixed("ban-history-points", 3L);
            case SWARM_TRACKER -> fixed("swarm-tracker-points", 3L);
            case RULES_FETCH -> fixed("rules-fetch-points", 2L);
            case KEEPALIVE -> fixed("keepalive-points", 1L);
            case RULE_HIT_IP -> fixed("rule-hit-ip-points", 10L);
            case NEW_IP -> fixed("new-ip-points", 20L);
            case WASTED_UPLOAD -> fixed("wasted-upload-points-per-mib", 10L);
            case NEW_TORRENT -> once("new-torrent-points", 500L);
            case CLIENT_DISCOVERY -> once("client-discovery-points", 4L);
            case DYNAMIC_SEED_UPLOAD -> dynamic("dynamic-seed-upload-points", 180L);
            case DYNAMIC_DOWNLOAD -> dynamic("dynamic-download-points", 50L);
            case DYNAMIC_HOT_PEER -> dynamic("dynamic-hot-peer-points", 100L);
        };
    }

    public Long dailyCap(PointCategory category) {
        return switch (category) {
            case LOGIN -> fixedCap("login-daily-cap", 1_000L);
            case BAN_HISTORY -> fixedCap("ban-history-daily-cap", 2_000L);
            case SWARM_TRACKER -> fixedCap("swarm-tracker-daily-cap", 2_000L);
            case RULES_FETCH -> fixedCap("rules-fetch-daily-cap", 2_000L);
            case KEEPALIVE -> fixedCap("keepalive-daily-cap", 2_000L);
            case RULE_HIT_IP -> fixedCap("rule-hit-ip-daily-cap", 2_000L);
            case NEW_IP -> fixedCap("new-ip-daily-cap", 2_000L);
            case WASTED_UPLOAD -> fixedCap("wasted-upload-daily-cap", 3_000L);
            case ACTIVE_APP, APP_ONLINE, NEW_TORRENT, CLIENT_DISCOVERY,
                    DYNAMIC_SEED_UPLOAD, DYNAMIC_DOWNLOAD, DYNAMIC_HOT_PEER -> null;
        };
    }

    public int onlineGapMinutes() {
        return Math.max(1, configService.integerValue(
                FIXED_RULES_MODULE_ID,
                "app-online-gap-minutes",
                15
        ));
    }

    public long dynamicTorrentSizeGib() {
        return positive(dynamic("dynamic-torrent-size-gib", 1L));
    }

    public long wastedUploadMib() {
        return positive(fixed("wasted-upload-mib", 1L));
    }

    public long dynamicSeedUploadMib() {
        return positive(dynamic("dynamic-seed-upload-mib", 200L));
    }

    public long dynamicDownloadMib() {
        return positive(dynamic("dynamic-download-mib", 512L));
    }

    public int dynamicHotPeerTop() {
        return Math.max(1, (int) Math.min(
                Integer.MAX_VALUE,
                dynamic("dynamic-hot-peer-top", 50L)
        ));
    }

    private long fixed(String field, long fallback) {
        return positive(configService.longValue(FIXED_RULES_MODULE_ID, field, fallback));
    }

    private long fixedCap(String field, long fallback) {
        return Math.max(0, configService.longValue(
                FIXED_RULES_MODULE_ID,
                field,
                fallback
        ));
    }

    private long once(String field, long fallback) {
        return positive(configService.longValue(ONCE_RULES_MODULE_ID, field, fallback));
    }

    private long dynamic(String field, long fallback) {
        return positive(configService.longValue(DYNAMIC_RULES_MODULE_ID, field, fallback));
    }

    private long positive(long value) {
        return Math.max(1, value);
    }
}
