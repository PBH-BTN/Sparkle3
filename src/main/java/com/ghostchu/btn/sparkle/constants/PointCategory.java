package com.ghostchu.btn.sparkle.constants;

import lombok.Getter;

@Getter
public enum PointCategory {
    LOGIN(PointComponent.FIXED, 1_000L),
    ACTIVE_APP(PointComponent.FIXED, null),
    APP_ONLINE(PointComponent.FIXED, null),
    BAN_HISTORY(PointComponent.FIXED, 2_000L),
    SWARM_TRACKER(PointComponent.FIXED, 2_000L),
    RULES_FETCH(PointComponent.FIXED, 2_000L),
    KEEPALIVE(PointComponent.FIXED, 2_000L),
    RULE_HIT_IP(PointComponent.FIXED, 2_000L),
    NEW_IP(PointComponent.FIXED, 2_000L),
    WASTED_UPLOAD(PointComponent.FIXED, 3_000L),
    NEW_TORRENT(PointComponent.ONE_TIME, null),
    CLIENT_DISCOVERY(PointComponent.ONE_TIME, null),
    DYNAMIC_SEED_UPLOAD(PointComponent.DYNAMIC, null),
    DYNAMIC_DOWNLOAD(PointComponent.DYNAMIC, null),
    DYNAMIC_HOT_PEER(PointComponent.DYNAMIC, null);

    private final PointComponent component;
    private final Long dailyCap;

    PointCategory(PointComponent component, Long dailyCap) {
        this.component = component;
        this.dailyCap = dailyCap;
    }
}
