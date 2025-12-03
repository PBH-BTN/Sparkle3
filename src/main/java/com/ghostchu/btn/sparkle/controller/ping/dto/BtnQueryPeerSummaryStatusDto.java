package com.ghostchu.btn.sparkle.controller.ping.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BtnQueryPeerSummaryStatusDto {
    private String status;
    private String message;
    private Integer score;
}
