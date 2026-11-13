package com.ghostchu.btn.sparkle.service.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PeerVersionSnapshotRow {
    private Integer rangeHours;
    private Long ipv4Peers;
    private Long ipv6Peers;
}
