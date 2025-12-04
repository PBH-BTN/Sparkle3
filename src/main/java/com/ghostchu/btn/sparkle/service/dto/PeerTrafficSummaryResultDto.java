package com.ghostchu.btn.sparkle.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PeerTrafficSummaryResultDto {
    @JsonProperty("peer_ip")
    private String peerIp;
    @JsonProperty("sum_to_peer_traffic")
    private long sumToPeerTraffic;
    @JsonProperty("sum_from_peer_traffic")
    private long sumFromPeerTraffic;
}
