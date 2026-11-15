package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.NetworkTrafficAccess;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminConfigService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final String MIN_PROTOCOL = "sparkle.ping.config.min-protocol-version";
    private static final String MAX_PROTOCOL = "sparkle.ping.config.max-protocol-version";
    private static final String QUERY_IP_POW = "sparkle.ping.query-ip.pow-captcha";
    private static final String CLIENT_DISCOVERY = "sparkle.client-discovery.enabled";
    private static final String SWARM_STATISTICS_ENABLED = "sparkle.swarm-statistics-track.enabled";
    private static final String SWARM_STATISTICS_REFRESH_MINUTES =
            "sparkle.swarm-statistics-track.refresh-interval-minutes";
    private static final String SWARM_STATISTICS_WINDOW_DAYS =
            "sparkle.swarm-statistics-track.window-days";
    private static final String HOMEPAGE_NETWORK_TRAFFIC_ENABLED = "sparkle.homepage.network-traffic-enabled";
    private static final String HOMEPAGE_NETWORK_TRAFFIC_STAFF_ONLY = "sparkle.homepage.network-traffic-staff-only";
    private static final String HOMEPAGE_TOTAL_LOSS_STAFF_ONLY = "sparkle.homepage.total-loss-staff-only";
    private static final String HOMEPAGE_REPORTED_STAFF_ONLY = "sparkle.homepage.reported-staff-only";
    private static final String HOMEPAGE_CONFIRMED_STAFF_ONLY = "sparkle.homepage.confirmed-staff-only";
    private static final String HOMEPAGE_ACTIVITY_STAFF_ONLY = "sparkle.homepage.activity-staff-only";
    private static final String HOMEPAGE_AVERAGE_LOSS_STAFF_ONLY = "sparkle.homepage.average-loss-staff-only";
    private static final String USER_CONTACT_EMAIL_SUBJECT_TEMPLATE =
            "sparkle.user-contact-email.subject-template";
    private static final String USER_CONTACT_EMAIL_BODY_TEMPLATE =
            "sparkle.user-contact-email.content-template";

    private final DatabaseConfigService databaseConfigService;
    private final RuntimeConfigView defaults;
    private volatile RuntimeConfigView cached;
    private volatile long cacheExpiresAt;

    public AdminConfigService(DatabaseConfigService databaseConfigService,
                              @Value("${sparkle.ping.config.min-protocol-version}") int protocolMinVersion,
                              @Value("${sparkle.ping.config.max-protocol-version}") int protocolMaxVersion,
                              @Value("${sparkle.ping.query-ip.pow-captcha}") boolean queryIpPowCaptcha,
                              @Value("${sparkle.client-discovery.enabled}") boolean clientDiscoveryEnabled,
                              @Value("${sparkle.swarm-statistics-track.enabled:true}")
                              boolean swarmStatisticsEnabled,
                              @Value("${sparkle.swarm-statistics-track.refresh-interval-minutes:60}")
                              int swarmStatisticsRefreshMinutes,
                              @Value("${sparkle.swarm-statistics-track.window-days:7}")
                              int swarmStatisticsWindowDays,
                              @Value("${sparkle.homepage.network-traffic-enabled:true}")
                              boolean homepageNetworkTrafficEnabled,
                              @Value("${sparkle.homepage.network-traffic-staff-only:false}") boolean homepageNetworkTrafficStaffOnly,
                              @Value("${sparkle.homepage.total-loss-staff-only:true}") boolean homepageTotalLossStaffOnly,
                              @Value("${sparkle.user-contact-email.subject-template}")
                              String userContactEmailSubjectTemplate,
                              @Value("${sparkle.user-contact-email.content-template}")
                              String userContactEmailBodyTemplate) {
        this.databaseConfigService = databaseConfigService;
        this.defaults = new RuntimeConfigView(
                protocolMinVersion,
                protocolMaxVersion,
                queryIpPowCaptcha,
                clientDiscoveryEnabled,
                swarmStatisticsEnabled,
                swarmStatisticsRefreshMinutes,
                swarmStatisticsWindowDays,
                homepageNetworkTrafficEnabled,
                homepageNetworkTrafficStaffOnly,
                homepageTotalLossStaffOnly,
                homepageNetworkTrafficStaffOnly,
                homepageNetworkTrafficStaffOnly,
                homepageNetworkTrafficStaffOnly,
                homepageNetworkTrafficStaffOnly,
                userContactEmailSubjectTemplate,
                userContactEmailBodyTemplate
        );
    }

    public @NotNull RuntimeConfigView get() {
        long now = System.currentTimeMillis();
        if (cached != null && now < cacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (cached != null && now < cacheExpiresAt) {
                return cached;
            }
            cached = load();
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return cached;
        }
    }

    public NetworkTrafficAccess homepageTrafficAccess(User user) {
        if (user == null || user.getBannedAt() != null) {
            return new NetworkTrafficAccess(false, false, false, false, false);
        }
        var config = get();
        boolean staff = AdminPolicy.hasModeratorAccess(user);
        boolean trafficEnabled = effectiveBoolean(HOMEPAGE_NETWORK_TRAFFIC_ENABLED,
                config.homepageNetworkTrafficEnabled(), defaults.homepageNetworkTrafficEnabled());
        return new NetworkTrafficAccess(
                trafficEnabled && (staff || !effectiveAudience(HOMEPAGE_REPORTED_STAFF_ONLY, config.homepageReportedStaffOnly())),
                trafficEnabled && (staff || !effectiveAudience(HOMEPAGE_CONFIRMED_STAFF_ONLY, config.homepageConfirmedStaffOnly())),
                trafficEnabled && (staff || !effectiveAudience(HOMEPAGE_ACTIVITY_STAFF_ONLY, config.homepageActivityStaffOnly())),
                trafficEnabled && (staff || !effectiveAudience(HOMEPAGE_AVERAGE_LOSS_STAFF_ONLY, config.homepageAverageLossStaffOnly())),
                staff || !effectiveBoolean(HOMEPAGE_TOTAL_LOSS_STAFF_ONLY,
                        config.homepageTotalLossStaffOnly(), defaults.homepageTotalLossStaffOnly())
        );
    }

    private boolean effectiveBoolean(String key, boolean configured, boolean fallback) {
        return databaseConfigService.hasExternalOverride(key, String.valueOf(configured)) ? fallback : configured;
    }

    private boolean effectiveAudience(String key, boolean configured) {
        return parseBoolean(databaseConfigService.getExternalOverride(key, String.valueOf(configured)), configured);
    }

    @Transactional
    public @NotNull RuntimeConfigView update(@NotNull RuntimeConfigView config, Long operatorId)
            throws BusinessException {
        validate(config);
        RuntimeConfigView normalized = normalized(config);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MIN_PROTOCOL, String.valueOf(normalized.protocolMinVersion()));
        values.put(MAX_PROTOCOL, String.valueOf(normalized.protocolMaxVersion()));
        values.put(QUERY_IP_POW, String.valueOf(normalized.queryIpPowCaptcha()));
        values.put(CLIENT_DISCOVERY, String.valueOf(normalized.clientDiscoveryEnabled()));
        values.put(SWARM_STATISTICS_ENABLED, String.valueOf(normalized.swarmStatisticsEnabled()));
        values.put(SWARM_STATISTICS_REFRESH_MINUTES,
                String.valueOf(normalized.swarmStatisticsRefreshMinutes()));
        values.put(SWARM_STATISTICS_WINDOW_DAYS, String.valueOf(normalized.swarmStatisticsWindowDays()));
        if (config.homepageNetworkTrafficEnabled() != null) {
            values.put(HOMEPAGE_NETWORK_TRAFFIC_ENABLED, String.valueOf(normalized.homepageNetworkTrafficEnabled()));
        }
        if (config.homepageNetworkTrafficStaffOnly() != null) {
            values.put(HOMEPAGE_NETWORK_TRAFFIC_STAFF_ONLY, String.valueOf(normalized.homepageNetworkTrafficStaffOnly()));
        }
        if (config.homepageTotalLossStaffOnly() != null) {
            values.put(HOMEPAGE_TOTAL_LOSS_STAFF_ONLY, String.valueOf(normalized.homepageTotalLossStaffOnly()));
        }
        addNewAudienceSetting(values, HOMEPAGE_REPORTED_STAFF_ONLY, config.homepageReportedStaffOnly());
        addNewAudienceSetting(values, HOMEPAGE_CONFIRMED_STAFF_ONLY, config.homepageConfirmedStaffOnly());
        addNewAudienceSetting(values, HOMEPAGE_ACTIVITY_STAFF_ONLY, config.homepageActivityStaffOnly());
        addNewAudienceSetting(values, HOMEPAGE_AVERAGE_LOSS_STAFF_ONLY, config.homepageAverageLossStaffOnly());
        values.put(USER_CONTACT_EMAIL_SUBJECT_TEMPLATE,
                normalized.userContactEmailSubjectTemplate());
        values.put(USER_CONTACT_EMAIL_BODY_TEMPLATE, normalized.userContactEmailBodyTemplate());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            databaseConfigService.update(entry.getKey(), entry.getValue(), operatorId);
        }
        cached = normalized;
        cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL.toMillis();
        return cached;
    }

    private void addNewAudienceSetting(Map<String, String> values, String key, Boolean staffOnly) {
        if (staffOnly == null) return;
        String value = staffOnly.toString();
        databaseConfigService.insertIfMissing(key, value, "database");
        values.put(key, value);
    }

    private RuntimeConfigView load() {
        try {
            Map<String, String> entries = databaseConfigService.getRawValues(List.of(
                    MIN_PROTOCOL,
                    MAX_PROTOCOL,
                    QUERY_IP_POW,
                    CLIENT_DISCOVERY,
                    SWARM_STATISTICS_ENABLED,
                    SWARM_STATISTICS_REFRESH_MINUTES,
                    SWARM_STATISTICS_WINDOW_DAYS,
                    HOMEPAGE_NETWORK_TRAFFIC_ENABLED,
                    HOMEPAGE_NETWORK_TRAFFIC_STAFF_ONLY,
                    HOMEPAGE_TOTAL_LOSS_STAFF_ONLY,
                    HOMEPAGE_REPORTED_STAFF_ONLY,
                    HOMEPAGE_CONFIRMED_STAFF_ONLY,
                    HOMEPAGE_ACTIVITY_STAFF_ONLY,
                    HOMEPAGE_AVERAGE_LOSS_STAFF_ONLY,
                    USER_CONTACT_EMAIL_SUBJECT_TEMPLATE,
                    USER_CONTACT_EMAIL_BODY_TEMPLATE
            ));
            boolean legacyStaffOnly = parseBoolean(entries.get(HOMEPAGE_NETWORK_TRAFFIC_STAFF_ONLY),
                    defaults.homepageNetworkTrafficStaffOnly());
            boolean legacyAudience = effectiveBoolean(HOMEPAGE_NETWORK_TRAFFIC_STAFF_ONLY, legacyStaffOnly,
                    defaults.homepageNetworkTrafficStaffOnly());
            return new RuntimeConfigView(
                    parseInt(entries.get(MIN_PROTOCOL), defaults.protocolMinVersion()),
                    parseInt(entries.get(MAX_PROTOCOL), defaults.protocolMaxVersion()),
                    parseBoolean(entries.get(QUERY_IP_POW), defaults.queryIpPowCaptcha()),
                    parseBoolean(entries.get(CLIENT_DISCOVERY), defaults.clientDiscoveryEnabled()),
                    parseBoolean(entries.get(SWARM_STATISTICS_ENABLED), defaults.swarmStatisticsEnabled()),
                    parseInt(entries.get(SWARM_STATISTICS_REFRESH_MINUTES),
                            defaults.swarmStatisticsRefreshMinutes()),
                    parseInt(entries.get(SWARM_STATISTICS_WINDOW_DAYS),
                            defaults.swarmStatisticsWindowDays()),
                    parseBoolean(entries.get(HOMEPAGE_NETWORK_TRAFFIC_ENABLED), defaults.homepageNetworkTrafficEnabled()),
                    legacyStaffOnly,
                    parseBoolean(entries.get(HOMEPAGE_TOTAL_LOSS_STAFF_ONLY), defaults.homepageTotalLossStaffOnly()),
                    parseBoolean(entries.get(HOMEPAGE_REPORTED_STAFF_ONLY), legacyAudience),
                    parseBoolean(entries.get(HOMEPAGE_CONFIRMED_STAFF_ONLY), legacyAudience),
                    parseBoolean(entries.get(HOMEPAGE_ACTIVITY_STAFF_ONLY), legacyAudience),
                    parseBoolean(entries.get(HOMEPAGE_AVERAGE_LOSS_STAFF_ONLY), legacyAudience),
                    entries.getOrDefault(
                            USER_CONTACT_EMAIL_SUBJECT_TEMPLATE,
                            defaults.userContactEmailSubjectTemplate()
                    ),
                    entries.getOrDefault(
                            USER_CONTACT_EMAIL_BODY_TEMPLATE,
                            defaults.userContactEmailBodyTemplate()
                    )
            );
        } catch (Exception exception) {
            return cached == null ? defaults : cached;
        }
    }

    private void validate(RuntimeConfigView config) throws BusinessException {
        if (config.protocolMinVersion() < 1
                || config.protocolMaxVersion() < config.protocolMinVersion()
                || config.protocolMaxVersion() > 10_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid BTN protocol version range");
        }
        if (config.swarmStatisticsRefreshMinutes() < 1
                || config.swarmStatisticsRefreshMinutes() > 10_080) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Swarm statistics refresh interval must be between 1 and 10080 minutes");
        }
        if (config.swarmStatisticsWindowDays() < 1
                || config.swarmStatisticsWindowDays() > 90) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Swarm statistics window must be between 1 and 90 days");
        }
        if (config.userContactEmailSubjectTemplate() == null
                || config.userContactEmailSubjectTemplate().isBlank()
                || config.userContactEmailSubjectTemplate().length() > 255
                || !config.userContactEmailSubjectTemplate().contains("{{title}}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Contact email subject template must contain {{title}} and be at most 255 characters");
        }
        if (config.userContactEmailBodyTemplate() == null
                || config.userContactEmailBodyTemplate().isBlank()
                || config.userContactEmailBodyTemplate().length() > 20_000
                || !config.userContactEmailBodyTemplate().contains("{{content}}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Contact email body template must contain {{content}} and be at most 20000 characters");
        }
    }

    private RuntimeConfigView normalized(RuntimeConfigView config) {
        var current = get();
        return new RuntimeConfigView(
                config.protocolMinVersion(),
                config.protocolMaxVersion(),
                config.queryIpPowCaptcha(),
                config.clientDiscoveryEnabled(),
                config.swarmStatisticsEnabled(),
                config.swarmStatisticsRefreshMinutes(),
                config.swarmStatisticsWindowDays(),
                config.homepageNetworkTrafficEnabled() == null
                        ? current.homepageNetworkTrafficEnabled() : config.homepageNetworkTrafficEnabled(),
                config.homepageNetworkTrafficStaffOnly() == null
                        ? current.homepageNetworkTrafficStaffOnly() : config.homepageNetworkTrafficStaffOnly(),
                config.homepageTotalLossStaffOnly() == null
                        ? current.homepageTotalLossStaffOnly() : config.homepageTotalLossStaffOnly(),
                config.homepageReportedStaffOnly() == null ? current.homepageReportedStaffOnly() : config.homepageReportedStaffOnly(),
                config.homepageConfirmedStaffOnly() == null ? current.homepageConfirmedStaffOnly() : config.homepageConfirmedStaffOnly(),
                config.homepageActivityStaffOnly() == null ? current.homepageActivityStaffOnly() : config.homepageActivityStaffOnly(),
                config.homepageAverageLossStaffOnly() == null ? current.homepageAverageLossStaffOnly() : config.homepageAverageLossStaffOnly(),
                config.userContactEmailSubjectTemplate().trim(),
                config.userContactEmailBodyTemplate().trim()
        );
    }

    private int parseInt(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

}
