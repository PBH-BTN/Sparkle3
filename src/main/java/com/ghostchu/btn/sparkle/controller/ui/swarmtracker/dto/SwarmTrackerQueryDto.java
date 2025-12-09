package com.ghostchu.btn.sparkle.controller.ui.swarmtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SwarmTrackerQueryDto implements Serializable {
    private String infoHash;
    private String peerIp;
    private Integer peerPort;
    private String peerId;
    private String peerClientName;
    private Double peerProgress;
    private Long fromPeerTraffic;
    private Long toPeerTraffic;
    private String flags;
    private String firstTimeSeenAfter;
    private String lastTimeSeenAfter;
    private Double userProgress;
    private String sortBy = "last_time_seen";
    private String sortOrder = "desc";
    private int page = 1;
    private int size = 100;
}
