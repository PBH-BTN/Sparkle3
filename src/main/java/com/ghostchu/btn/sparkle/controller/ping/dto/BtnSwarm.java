package com.ghostchu.btn.sparkle.controller.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnSwarm {
    @JsonProperty("torrent_identifier")
    private String torrentIdentifier;
    @JsonProperty("torrent_is_private")
    private Boolean torrentIsPrivate;
    @JsonProperty("torrent_size")
    private long torrentSize;
    @JsonProperty("downloader")
    private String downloader;
    @JsonProperty("downloader_progress")
    private double downloaderProgress;
    @JsonProperty("peer_ip")
    private String peerIp;
    @JsonProperty("peer_port")
    private int port;
    @JsonProperty("peer_id")
    private String peerId;
    @JsonProperty("peer_client_name")
    private String clientName;
    @JsonProperty("peer_progress")
    private double peerProgress;
    @JsonProperty("to_peer_traffic")
    private long toPeerTraffic;
    @JsonProperty("to_peer_traffic_offset")
    private long toPeerTrafficOffset;
    @JsonProperty("from_peer_traffic")
    private long fromPeerTraffic;
    @JsonProperty("from_peer_traffic_offset")
    private long fromPeerTrafficOffset;
    @JsonProperty("first_time_seen")
    private Timestamp firstTimeSeen;
    @JsonProperty("last_time_seen")
    private Timestamp lastTimeSeen;
    @JsonProperty("peer_last_flags")
    private String peerLastFlags;
}
