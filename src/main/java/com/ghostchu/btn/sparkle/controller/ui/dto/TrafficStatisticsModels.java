package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.util.Map;

public final class TrafficStatisticsModels {
    private TrafficStatisticsModels() { }
    public record TrafficPage(java.util.List<DailyTraffic> records, long current, int size, long total, long pages) { }
    public record DailyTraffic(String day, Map<String, String> metrics) { }
}
