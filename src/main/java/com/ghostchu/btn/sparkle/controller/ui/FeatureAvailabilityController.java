package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.FeatureModels.FeatureAvailabilityView;
import com.ghostchu.btn.sparkle.service.AfdianSettingsService;
import com.ghostchu.btn.sparkle.service.MemberPlatformService;
import com.ghostchu.btn.sparkle.service.github.CommunityGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui/features")
@RequiredArgsConstructor
public class FeatureAvailabilityController {
    private final AfdianSettingsService afdianSettingsService;
    private final CommunityGroupService communityGroupService;
    private final MemberPlatformService memberPlatformService;

    @GetMapping
    public FeatureAvailabilityView features() {
        return new FeatureAvailabilityView(
                afdianSettingsService.configured(),
                communityGroupService.hasEnabledGroups(),
                memberPlatformService.anyConfigured()
        );
    }
}
