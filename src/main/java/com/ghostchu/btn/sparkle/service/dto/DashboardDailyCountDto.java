package com.ghostchu.btn.sparkle.service.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DashboardDailyCountDto {
    private OffsetDateTime bucket;
    private String name;
    private long value;
}
