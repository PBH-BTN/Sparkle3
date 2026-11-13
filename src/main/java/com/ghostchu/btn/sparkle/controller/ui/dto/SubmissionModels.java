package com.ghostchu.btn.sparkle.controller.ui.dto;

import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;

public final class SubmissionModels {
    private SubmissionModels() {}

    @Data
    public static class SubmissionRecord {
        private String id;
        private String application;
        private OffsetDateTime submittedAt;
        private String peerId;
        private String clientName;
        private String moduleName;
        private String clientType;
        private String clientVersion;
        private String torrentId;
        private String uploaded;
        private String downloaded;
        private OffsetDateTime insertTime;
        private OffsetDateTime populateTime;
        private String peerIp;
        private Integer peerPort;
        private Double peerProgress;
        private Double reporterProgress;
        private String flags;
        private IPGeoData peerGeoip;
        private String rule;
        private String description;
        private Map<String, Object> structuredData;
        private OffsetDateTime firstTimeSeen;
        private OffsetDateTime lastTimeSeen;
        private Double userProgress;
    }
}
