package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminSettingsModels.*;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AdminSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {
    private final AdminSettingsService settings;

    @GetMapping("/statistics")
    public StatisticsSettings statistics() {
        return settings.statistics();
    }

    @PutMapping("/statistics")
    public StatisticsSettings updateStatistics(@RequestBody StatisticsSettings body,
                                               @AuthenticationPrincipal SparkleUserDetails principal) throws BusinessException {
        return settings.updateStatistics(body, principal.getUserId());
    }

    @GetMapping("/homepage-display")
    public HomepageDisplaySettings homepageDisplay() {
        return settings.homepageDisplay();
    }

    @PutMapping("/homepage-display")
    public HomepageDisplaySettings updateHomepageDisplay(@RequestBody HomepageDisplaySettings body,
                                                        @AuthenticationPrincipal SparkleUserDetails principal) throws BusinessException {
        return settings.updateHomepageDisplay(body, principal.getUserId());
    }

    @GetMapping("/contact-template")
    public ContactTemplateSettings contactTemplate() {
        return settings.contactTemplate();
    }

    @PutMapping("/contact-template")
    public ContactTemplateSettings updateContactTemplate(@RequestBody ContactTemplateSettings body,
                                                        @AuthenticationPrincipal SparkleUserDetails principal) throws BusinessException {
        return settings.updateContactTemplate(body, principal.getUserId());
    }

    @GetMapping("/features")
    public SiteFeatureSettings features() {
        return settings.features();
    }

    @PutMapping("/features")
    public SiteFeatureSettings updateFeatures(@RequestBody SiteFeatureSettings body,
                                              @AuthenticationPrincipal SparkleUserDetails principal) throws BusinessException {
        return settings.updateFeatures(body, principal.getUserId());
    }
}
