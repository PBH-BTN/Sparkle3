package com.ghostchu.btn.sparkle.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BanHistoryDto {
    @JsonProperty("populate_time")
    private Long populateTime;
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
    @JsonProperty("peer_flags")
    private String peerFlags;
    @JsonProperty("reporter_progress")
    private Double reporterProgress;
    @JsonProperty("to_peer_traffic")
    private Long toPeerTraffic;
    @JsonProperty("from_peer_traffic")
    private Long fromPeerTraffic;
    @JsonProperty("module_name")
    private String moduleName;
    @JsonProperty("rule")
    private String rule;
    @JsonProperty("description")
    private String description;
    @JsonProperty("structured_data")
    private Map<String, Object> structuredData;

    public BanHistoryDto(BanHistory banHistory) {
        this.torrent = String.valueOf(banHistory.getTorrentId());
        this.populateTime = banHistory.getPopulateTime().toInstant().toEpochMilli();
        this.peerIp = banHistory.getPeerIp();
        this.peerPort = banHistory.getPeerPort();
        this.peerId = banHistory.getPeerId();
        this.peerClientName = banHistory.getPeerClientName();
        this.peerProgress = banHistory.getPeerProgress();
        this.peerFlags = banHistory.getPeerFlags();
        this.reporterProgress = banHistory.getReporterProgress();
        this.toPeerTraffic = banHistory.getToPeerTraffic();
        this.fromPeerTraffic = banHistory.getFromPeerTraffic();
        this.moduleName = banHistory.getModuleName();
        this.rule = banHistory.getRule();
        this.description = banHistory.getDescription();
        this.structuredData = banHistory.getStructuredData();
    }
}
