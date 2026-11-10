package com.ghostchu.btn.sparkle.service.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DashboardGeoIpDailyCountDto {
    private OffsetDateTime bucket;
    private String city;
    private int ipVersion;
    private long value;
}
