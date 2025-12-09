package com.ghostchu.btn.sparkle.controller.ui.clientdiscovery.dto;

import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientDiscoveryDto implements Serializable {
    private Long hash;
    private String peerId;
    private String peerClientName;
    private OffsetDateTime foundAt;
    private Long foundUserappsId;
    private String clientType;
    private String clientSemver;

    public ClientDiscoveryDto(ClientDiscovery clientDiscovery) {
        this.hash = clientDiscovery.getHash();
        this.peerId = clientDiscovery.getPeerId();
        this.peerClientName = clientDiscovery.getPeerClientName();
        this.foundAt = clientDiscovery.getFoundAt();
        this.foundUserappsId = clientDiscovery.getFoundUserappsId();
        this.clientType = clientDiscovery.getClientType();
        this.clientSemver = clientDiscovery.getClientSemver();
    }
}
