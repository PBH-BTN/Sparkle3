package com.ghostchu.btn.sparkle.mapper.customresult;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AnalyseConcurrentDownloadResult {
    private String peerIp;
    private long torrentCount;
    private long userappsCount;
}
