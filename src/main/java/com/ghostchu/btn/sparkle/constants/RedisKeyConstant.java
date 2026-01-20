package com.ghostchu.btn.sparkle.constants;

import lombok.Getter;

@Getter
public enum RedisKeyConstant {
    RULE_IP_DENYLIST_VERSION("sparkle:rule:ip_denylist:version"),
    RULE_IP_DENYLIST_VALUE("sparkle:rule:ip_denylist:value"),
    RULE_IP_ALLOWLIST_VERSION("sparkle:rule:ip_allowlist:version"),
    RULE_IP_ALLOWLIST_VALUE("sparkle:rule:ip_allowlist:value"),
    ANALYSE_UNTRUSTED_VOTE_VERSION("sparkle:analyse:untrusted_vote:version"),
    ANALYSE_UNTRUSTED_VOTE_VALUE("sparkle:analyse:untrusted_vote:value"),
    ANALYSE_OVER_DOWNLOAD_VOTE_VERSION("sparkle:analyse:over_download:version"),
    ANALYSE_OVER_DOWNLOAD_VOTE_VALUE("sparkle:analyse:over_download:value"),
    ANALYSE_CONCURRENT_DOWNLOAD_VERSION("sparkle:analyse:concurrent_download:version"),
    ANALYSE_CONCURRENT_DOWNLOAD_VALUE("sparkle:analyse:concurrent_download:value"),
    ANALYSE_RANDOM_IDENTITY_VERSION("sparkle:analyse:random_identity:version"),
    ANALYSE_RANDOM_IDENTITY_VALUE("sparkle:analyse:random_identity:value"),
    ANALYSE_RAIN000_IDENTITY_VERSION("sparkle:analyse:rain000_identity:version"),
    ANALYSE_RAIN000_IDENTITY_VALUE("sparkle:analyse:rain000_identity:value"),
    ANALYSE_GOPEEDDEV_IDENTITY_VERSION("sparkle:analyse:gopeeddev_identity:version"),
    ANALYSE_GOPEEDDEV_IDENTITY_VALUE("sparkle:analyse:gopeeddev_identity:value"),
    ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VERSION("sparkle:analyse:datacenter_high_risk_identity:version"),
    ANALYSE_DATACENTER_HIGH_RISK_IDENTITY_VALUE("sparkle:analyse:datacenter_high_risk_identity:value"),
    STATS_BANHISTORY_ALLTIME("sparkle:stats:banhistory:alltime"),
    STATS_BANHISTORY_30DAYS("sparkle:stats:banhistory:30days"),
    STATS_BANHISTORY_14DAYS("sparkle:stats:banhistory:14days"),
    STATS_BANHISTORY_7DAYS("sparkle:stats:banhistory:7days"),
    STATS_BANHISTORY_24HOURS("sparkle:stats:banhistory:24hours"),
    STATS_SWARMTRACKER_ALLTIME("sparkle:stats:swarmtracker:alltime"),
    STATS_SWARMTRACKER_30DAYS("sparkle:stats:swarmtracker:30days"),
    STATS_SWARMTRACKER_14DAYS("sparkle:stats:swarmtracker:14days"),
    STATS_SWARMTRACKER_7DAYS("sparkle:stats:swarmtracker:7days"),
    STATS_SWARMTRACKER_24HOURS("sparkle:stats:swarmtracker:24hours"),
    STATS_USERAPP_30DAYS("sparkle:stats:userapp:30days"),
    STATS_USERAPP_14DAYS("sparkle:stats:userapp:14days"),
    STATS_USERAPP_7DAYS("sparkle:stats:userapp:7days"),
    STATS_USERAPP_24HOURS("sparkle:stats:userapp:24hours"),
    STATS_TORRENT_ALLTIME("sparkle:stats:torrent:alltime"),
    STATS_TORRENT_30DAYS("sparkle:stats:torrent:30days"),
    STATS_TORRENT_14DAYS("sparkle:stats:torrent:14days"),
    STATS_TORRENT_7DAYS("sparkle:stats:torrent:7days"),
    STATS_TORRENT_24HOURS("sparkle:stats:torrent:24hours"),
    ADMIN_USER_IP_HEARTBEAT_WARNING_LIST("sparkle:admin:user:ip_heartbeat_warning_list");

    private final String key;

    RedisKeyConstant(String key) {
        this.key = key;
    }

}
