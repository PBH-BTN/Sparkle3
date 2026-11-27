package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminSettingsModels.*;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

/** Independent admin forms only write the keys they own. */
@Service
@RequiredArgsConstructor
public class AdminSettingsService {
    private final AdminConfigService runtime;
    private final DatabaseConfigService database;

    public StatisticsSettings statistics() {
        var config = runtime.get();
        return new StatisticsSettings(config.swarmStatisticsEnabled(),
                config.swarmStatisticsRefreshMinutes(), config.swarmStatisticsWindowDays());
    }

    @Transactional(rollbackFor = Exception.class)
    public StatisticsSettings updateStatistics(StatisticsSettings settings, Long operator) throws BusinessException {
        if (settings.refreshMinutes() < 1 || settings.refreshMinutes() > 10_080
                || settings.windowDays() < 1 || settings.windowDays() > 90) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid statistics interval or window");
        }
        save(Map.of(
                "sparkle.swarm-statistics-track.enabled", String.valueOf(settings.enabled()),
                "sparkle.swarm-statistics-track.refresh-interval-minutes", String.valueOf(settings.refreshMinutes()),
                "sparkle.swarm-statistics-track.window-days", String.valueOf(settings.windowDays())
        ), operator);
        return settings;
    }

    public HomepageDisplaySettings homepageDisplay() {
        var config = runtime.get();
        return new HomepageDisplaySettings(Boolean.TRUE.equals(config.homepageNetworkTrafficEnabled()),
                Boolean.TRUE.equals(config.homepageReportedStaffOnly()),
                Boolean.TRUE.equals(config.homepageConfirmedStaffOnly()),
                Boolean.TRUE.equals(config.homepageActivityStaffOnly()),
                Boolean.TRUE.equals(config.homepageAverageLossStaffOnly()),
                Boolean.TRUE.equals(config.homepageTotalLossStaffOnly()));
    }

    @Transactional(rollbackFor = Exception.class)
    public HomepageDisplaySettings updateHomepageDisplay(HomepageDisplaySettings settings, Long operator)
            throws BusinessException {
        save(Map.of(
                "sparkle.homepage.network-traffic-enabled", String.valueOf(settings.enabled()),
                "sparkle.homepage.reported-staff-only", String.valueOf(settings.reportedStaffOnly()),
                "sparkle.homepage.confirmed-staff-only", String.valueOf(settings.confirmedStaffOnly()),
                "sparkle.homepage.activity-staff-only", String.valueOf(settings.activityStaffOnly()),
                "sparkle.homepage.average-loss-staff-only", String.valueOf(settings.averageLossStaffOnly()),
                "sparkle.homepage.total-loss-staff-only", String.valueOf(settings.totalLossStaffOnly())
        ), operator);
        return settings;
    }

    public ContactTemplateSettings contactTemplate() {
        var config = runtime.get();
        return new ContactTemplateSettings(config.userContactEmailSubjectTemplate(), config.userContactEmailBodyTemplate());
    }

    @Transactional(rollbackFor = Exception.class)
    public ContactTemplateSettings updateContactTemplate(ContactTemplateSettings settings, Long operator)
            throws BusinessException {
        if (settings.subject() == null || settings.subject().isBlank() || settings.subject().length() > 255
                || !settings.subject().contains("{{title}}") || settings.body() == null
                || settings.body().isBlank() || settings.body().length() > 20_000
                || !settings.body().contains("{{content}}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid contact email template");
        }
        save(Map.of("sparkle.user-contact-email.subject-template", settings.subject(),
                "sparkle.user-contact-email.content-template", settings.body()), operator);
        return settings;
    }

    public SiteFeatureSettings features() {
        return new SiteFeatureSettings(runtime.get().clientDiscoveryEnabled());
    }

    @Transactional(rollbackFor = Exception.class)
    public SiteFeatureSettings updateFeatures(SiteFeatureSettings settings, Long operator) throws BusinessException {
        save(Map.of("sparkle.client-discovery.enabled", String.valueOf(settings.clientDiscoveryEnabled())), operator);
        return settings;
    }

    private void save(Map<String, String> values, Long operator) throws BusinessException {
        for (var entry : new TreeMap<>(values).entrySet()) {
            database.insertIfMissing(entry.getKey(), entry.getValue(), "database");
            database.update(entry.getKey(), entry.getValue(), operator);
        }
        runtime.invalidateAfterCommit();
    }
}
