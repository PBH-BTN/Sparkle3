package com.ghostchu.btn.sparkle.constants;

import lombok.Getter;

@Getter
public enum PointCategory {
    LOGIN(PointComponent.FIXED),
    ACTIVE_APP(PointComponent.FIXED),
    APP_ONLINE(PointComponent.FIXED),
    BAN_HISTORY(PointComponent.FIXED),
    SWARM_TRACKER(PointComponent.FIXED),
    RULES_FETCH(PointComponent.FIXED),
    KEEPALIVE(PointComponent.FIXED),
    RULE_HIT_IP(PointComponent.FIXED),
    WASTED_UPLOAD(PointComponent.FIXED),
    DYNAMIC_SEED_UPLOAD(PointComponent.DYNAMIC),
    DYNAMIC_DOWNLOAD(PointComponent.DYNAMIC),
    DYNAMIC_HOT_PEER(PointComponent.DYNAMIC);

    private final PointComponent component;

    PointCategory(PointComponent component) {
        this.component = component;
    }
}
