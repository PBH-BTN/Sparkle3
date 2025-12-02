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
    ANALYSE_CONCURRENT_DOWNLOAD_VALUE("sparkle:analyse:concurrent_download:value");

    private final String key;

    RedisKeyConstant(String key) {
        this.key = key;
    }

}
