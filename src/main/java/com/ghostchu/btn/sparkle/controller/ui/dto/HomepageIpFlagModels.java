package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.util.Map;

public final class HomepageIpFlagModels {
    private HomepageIpFlagModels() {
    }

    public record DailyIpFlags(String day, Map<String, Long> counts) { }
}
