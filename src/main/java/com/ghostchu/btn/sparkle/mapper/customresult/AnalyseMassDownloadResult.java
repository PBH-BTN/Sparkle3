package com.ghostchu.btn.sparkle.mapper.customresult;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AnalyseMassDownloadResult {
    private String peerIpCidr;
    private String downloadBytes;
    private Long reporterCount;
    private Long connectionCount;
    private Long torrentCount;
    private OffsetDateTime latestSeenAt;
}
