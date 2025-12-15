package com.ghostchu.btn.sparkle.mapper.customresult;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AnalyseByModuleResult {
    private String peerIpCidr;
    private String iso;
    private String city;
    private long banCount;
    private long userappsCount;
    private long toPeerTraffic;
    private long fromPeerTraffic;
}