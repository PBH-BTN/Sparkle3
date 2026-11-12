package com.ghostchu.btn.sparkle.controller.ui.userapp.dto;

import com.ghostchu.btn.sparkle.entity.Userapp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserApplicationDto implements Serializable {
    private Long id;
    private String appId;
    private String comment;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime bannedAt;
    private String bannedReason;
    private Boolean btnBypass;
    private List<String> exemptIps;

    public UserApplicationDto(Userapp userapp, List<String> exemptIps) {
        this.id = userapp.getId();
        this.appId = userapp.getAppId();
        this.comment = userapp.getComment();
        this.createdAt = userapp.getCreatedAt();
        this.lastSeenAt = userapp.getLastSeenAt();
        this.bannedAt = userapp.getBannedAt();
        this.bannedReason = userapp.getBannedReason();
        this.btnBypass = userapp.getBtnBypass();
        this.exemptIps = exemptIps == null ? List.of() : List.copyOf(exemptIps);
    }
}
