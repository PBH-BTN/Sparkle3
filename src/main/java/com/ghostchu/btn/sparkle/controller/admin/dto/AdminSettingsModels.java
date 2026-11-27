package com.ghostchu.btn.sparkle.controller.admin.dto;

public final class AdminSettingsModels {
    private AdminSettingsModels() {
    }

    public record StatisticsSettings(boolean enabled, int refreshMinutes, int windowDays) {
    }

    public record HomepageDisplaySettings(boolean enabled, boolean reportedStaffOnly,
                                          boolean confirmedStaffOnly, boolean activityStaffOnly,
                                          boolean averageLossStaffOnly, boolean totalLossStaffOnly) {
    }

    public record ContactTemplateSettings(String subject, String body) {
    }

    public record SiteFeatureSettings(boolean clientDiscoveryEnabled) {
    }
}
