package com.ghostchu.btn.sparkle.controller.ui.clientdiscovery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientDiscoveryQueryDto implements Serializable {
    private String peerId;
    private String peerClientName;
    private String clientType;
    private String clientSemver;
    private String sortBy = "found_at";
    private String sortOrder = "desc";
    private int page = 1;
    private int size = 20;
}
