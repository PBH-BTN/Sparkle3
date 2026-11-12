package com.ghostchu.btn.sparkle.service.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class HotTorrentRow {
    private Long torrentId;
    private Long peerCount;
    private Long ipv4PeerCount;
    private Long ipv6PeerCount;
    private Integer rank;
}
