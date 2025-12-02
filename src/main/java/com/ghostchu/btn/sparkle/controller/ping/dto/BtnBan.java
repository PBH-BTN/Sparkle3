package com.ghostchu.btn.sparkle.controller.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnBan {
    @JsonProperty("ban_at")
    private Timestamp banAt;
    @JsonProperty("peer_ip")
    private String peerIp;
    @JsonProperty("peer_port")
    private int peerPort;
    @JsonProperty("peer_id")
    private String peerId;
    @JsonProperty("peer_client_name")
    private String peerClientName;
    @JsonProperty("peer_progress")
    private double peerProgress;
    @JsonProperty("peer_flag")
    private String peerFlag;
    @JsonProperty("torrent_identifier")
    private String torrentIdentifier;
    @JsonProperty("torrent_is_private")
    private boolean torrentIsPrivate;
    @JsonProperty("torrent_size")
    private long torrentSize;
    @JsonProperty("from_peer_traffic")
    private long fromPeerTraffic;
    @JsonProperty("to_peer_traffic")
    private long toPeerTraffic;
    @JsonProperty("downloader_progress")
    private double downloaderProgress;
    @JsonProperty("module")
    private String module;
    @JsonProperty("rule")
    private String rule;
    @JsonProperty("description")
    private String description;
    @JsonProperty("structured_data")
    private String structuredData;
}
