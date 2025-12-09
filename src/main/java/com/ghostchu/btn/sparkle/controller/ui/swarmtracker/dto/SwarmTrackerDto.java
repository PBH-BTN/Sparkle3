package com.ghostchu.btn.sparkle.controller.ui.swarmtracker.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;

import com.ghostchu.btn.sparkle.entity.SwarmTracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SwarmTrackerDto implements Serializable {
    private Long id;
    private Long torrentId;
    private String peerIp;
    private Integer peerPort;
    private String peerId;
    private String peerClientName;
    private Double peerProgress;
    private Long fromPeerTraffic;
    private Long toPeerTraffic;
    private String flags;
    private OffsetDateTime firstTimeSeen;
    private OffsetDateTime lastTimeSeen;
    private Double userProgress;

    public SwarmTrackerDto(SwarmTracker swarmTracker) {
        this.id = swarmTracker.getId();
        this.torrentId = swarmTracker.getTorrentId();
        this.peerIp = swarmTracker.getPeerIp() != null ? swarmTracker.getPeerIp().getHostAddress() : null;
        this.peerPort = swarmTracker.getPeerPort();
        this.peerId = swarmTracker.getPeerId();
        this.peerClientName = swarmTracker.getPeerClientName();
        this.peerProgress = swarmTracker.getPeerProgress();
        this.fromPeerTraffic = swarmTracker.getFromPeerTraffic();
        this.toPeerTraffic = swarmTracker.getToPeerTraffic();
        this.flags = swarmTracker.getFlags();
        this.firstTimeSeen = swarmTracker.getFirstTimeSeen();
        this.lastTimeSeen = swarmTracker.getLastTimeSeen();
        this.userProgress = swarmTracker.getUserProgress();
    }
}
