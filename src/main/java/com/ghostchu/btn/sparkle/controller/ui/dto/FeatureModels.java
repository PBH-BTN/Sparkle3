package com.ghostchu.btn.sparkle.controller.ui.dto;

public final class FeatureModels {
    private FeatureModels() {
    }

    public record FeatureAvailabilityView(
            boolean afdianConfigured,
            boolean communityGroupsAvailable,
            boolean memberPlatformsConfigured
    ) {
    }
}
