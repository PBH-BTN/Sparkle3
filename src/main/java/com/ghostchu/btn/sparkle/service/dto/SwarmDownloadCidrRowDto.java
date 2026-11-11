package com.ghostchu.btn.sparkle.service.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SwarmDownloadCidrRowDto {
    private String cidr;
    private Integer ipVersion;
    private String downloadBytes;
    private Long reporterCount;
    private Long connectionCount;
    private Long torrentCount;
    private OffsetDateTime latestSeenAt;
    private Long matchingGroups;
}
