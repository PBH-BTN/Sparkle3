package com.ghostchu.btn.sparkle.controller.ui.banhistory.dto;

import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Ban History Response DTO
 * Used for displaying ban history records in UI
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BanHistoryResponseDto {
    private Long id;
    private OffsetDateTime insertTime;
    private Long torrentId;
    private String peerIp;
    private Integer peerPort;
    private String peerId;
    private String peerClientName;
    private Double peerProgress;
    private String peerFlags;
    private IPGeoData peerGeoip;
    private Double reporterProgress;
    private Long toPeerTraffic;
    private Long fromPeerTraffic;
    private String moduleName;
    private String rule;
    private String description;
    private Map<String, Object> structuredData;
    
    public BanHistoryResponseDto(BanHistory entity) {
        this.id = entity.getId();
        this.insertTime = entity.getInsertTime();
        this.torrentId = entity.getTorrentId();
        this.peerIp = entity.getPeerIp() != null ? entity.getPeerIp().getHostAddress() : null;
        this.peerPort = entity.getPeerPort();
        this.peerId = entity.getPeerId();
        this.peerClientName = entity.getPeerClientName();
        this.peerProgress = entity.getPeerProgress();
        this.peerFlags = entity.getPeerFlags();
        this.peerGeoip = entity.getPeerGeoip();
        this.reporterProgress = entity.getReporterProgress();
        this.toPeerTraffic = entity.getToPeerTraffic();
        this.fromPeerTraffic = entity.getFromPeerTraffic();
        this.moduleName = entity.getModuleName();
        this.rule = entity.getRule();
        this.description = entity.getDescription();
        this.structuredData = entity.getStructuredData();
    }
}
