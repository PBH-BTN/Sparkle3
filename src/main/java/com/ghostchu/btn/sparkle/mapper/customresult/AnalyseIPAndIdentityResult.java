package com.ghostchu.btn.sparkle.mapper.customresult;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AnalyseIPAndIdentityResult {
    private String peerIp;
    private String peerId;
    private String peerClientName;
}
