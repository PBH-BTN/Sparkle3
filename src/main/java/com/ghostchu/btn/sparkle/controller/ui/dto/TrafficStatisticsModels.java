package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.util.Map;

public final class TrafficStatisticsModels {
    public record NetworkTrafficAccess(boolean reported, boolean confirmed, boolean activity,
                                       boolean averageLoss, boolean totalLoss) {
        public boolean traffic() {
            return reported || confirmed || activity || averageLoss;
        }
    }
    private TrafficStatisticsModels() { }
    public record TrafficPage(java.util.List<DailyTraffic> records, long current, int size, long total, long pages) { }
    public record DailyTraffic(String day, Map<String, String> metrics) { }
}
