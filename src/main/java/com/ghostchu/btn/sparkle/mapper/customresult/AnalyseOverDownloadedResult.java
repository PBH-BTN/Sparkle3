package com.ghostchu.btn.sparkle.mapper.customresult;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AnalyseOverDownloadedResult {
    private String peerIp;
    private long torrentId;
    private long totalFromPeerTraffic;
    private long totalToPeerTraffic;
    private long torrentSize;
    private long pureToPeerTraffic;

}
