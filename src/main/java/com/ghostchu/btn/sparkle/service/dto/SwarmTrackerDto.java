package com.ghostchu.btn.sparkle.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SwarmTrackerDto {
    @JsonProperty("torrent")
    private String torrent;
    @JsonProperty("peer_ip")
    private InetAddress peerIp;
    @JsonProperty("peer_port")
    private Integer peerPort;
    @JsonProperty("peer_id")
    private String peerId;
    @JsonProperty("peer_client_name")
    private String peerClientName;
    @JsonProperty("peer_progress")
    private Double peerProgress;
    @JsonProperty("from_peer_traffic")
    private Long fromPeerTraffic;
    @JsonProperty("to_peer_traffic")
    private Long toPeerTraffic;
    @JsonProperty("from_peer_traffic_offset")
    private Long fromPeerTrafficOffset;
    @JsonProperty("to_peer_traffic_offset")
    private Long toPeerTrafficOffset;
    @JsonProperty("flags")
    private String flags;
    @JsonProperty(value = "first_time_seen")
    private Long firstTimeSeen;
    @JsonProperty(value = "last_time_seen")
    private Long lastTimeSeen;
    @JsonProperty("user_progress")
    private double userProgress;

    public SwarmTrackerDto(SwarmTracker swarmTracker) {
        this.torrent = "id=" + swarmTracker.getTorrentId();
        this.peerIp = swarmTracker.getPeerIp();
        this.peerPort = swarmTracker.getPeerPort();
        this.peerId = swarmTracker.getPeerId();
        this.peerClientName = swarmTracker.getPeerClientName();
        this.peerProgress = swarmTracker.getPeerProgress();
        this.fromPeerTraffic = swarmTracker.getFromPeerTraffic();
        this.toPeerTraffic = swarmTracker.getToPeerTraffic();
        this.fromPeerTrafficOffset = swarmTracker.getFromPeerTrafficOffset();
        this.toPeerTrafficOffset = swarmTracker.getToPeerTrafficOffset();
        this.flags = swarmTracker.getFlags();
        this.firstTimeSeen = swarmTracker.getFirstTimeSeen().toInstant().toEpochMilli();
        this.lastTimeSeen = swarmTracker.getLastTimeSeen().toInstant().toEpochMilli();
        this.userProgress = swarmTracker.getUserProgress();
    }
}
