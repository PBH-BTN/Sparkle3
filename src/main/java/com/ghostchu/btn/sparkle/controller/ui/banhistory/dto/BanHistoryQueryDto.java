package com.ghostchu.btn.sparkle.controller.ui.banhistory.dto;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ban History Query DTO
 * Used for filtering ban history records
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BanHistoryQueryDto {
    private OffsetDateTime insertTimeStart;
    private OffsetDateTime insertTimeEnd;
    private Long torrentId;
    private String peerIp;
    private Integer peerPort;
    private String peerId;
    private String peerClientName;
    private String moduleName;
    private String rule;
    private String description;
    
    // Pagination
    private Integer page = 1;
    private Integer size = 100;
    
    // Sorting
    private String sortBy = "insert_time";
    private String sortOrder = "desc";
}
