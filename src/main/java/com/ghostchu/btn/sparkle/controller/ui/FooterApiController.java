package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterAudience;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterColumnView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterMetricSetting;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterPerformanceView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.HttpResponsePerformanceView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.WorldTimeView;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.FooterConfigService;
import com.ghostchu.btn.sparkle.service.FooterMetricsService;
import com.ghostchu.btn.sparkle.service.FooterVisitorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui/footer")
@RequiredArgsConstructor
public class FooterApiController {
    private final FooterConfigService configService;
    private final FooterMetricsService metricsService;
    private final FooterVisitorService visitorService;

    @GetMapping
    public ResponseEntity<FooterResponse> footer(
            @AuthenticationPrincipal SparkleUserDetails principal,
            HttpServletRequest request
    ) {
        FooterConfigView config = configService.get();
        boolean authenticated = principal != null;
        boolean admin = AdminPolicy.isAdmin(principal);
        Map<String, FooterMetricSetting> metricSettings = metricSettings(config);

        boolean disclaimerVisible = canView(config.disclaimerAudience(), authenticated, admin);
        boolean icpVisible = canView(config.icpAudience(), authenticated, admin);
        boolean columnsVisible = canView(config.columnsAudience(), authenticated, admin);
        boolean timeVisible = canView(config.timeAudience(), authenticated, admin);
        boolean loginVisible = canView(config.loginAudience(), authenticated, admin);
        boolean anyPerformanceVisible = metricSettings.values().stream()
                .anyMatch(setting -> setting.enabled()
                        && canView(setting.audience(), authenticated, admin));

        FooterPerformanceView performance = anyPerformanceVisible
                ? redactPerformance(metricsService.snapshot(), metricSettings, authenticated, admin)
                : null;

        List<FooterColumnView> columns = columnsVisible
                ? config.columns() == null ? List.of() : config.columns()
                : List.of();
        FooterResponse response = new FooterResponse(
                disclaimerVisible ? config.disclaimerZh() : null,
                disclaimerVisible ? config.disclaimerEn() : null,
                icpVisible && config.icp() != null && !config.icp().isBlank() ? config.icp() : null,
                columns,
                timeVisible
                        ? new WorldTimeView(
                        System.currentTimeMillis(),
                        ZoneId.systemDefault().getId()
                )
                        : null,
                performance,
                loginVisible ? visitorService.visitor(request) : null
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Vary", "Cookie, Authorization")
                .body(response);
    }

    private Map<String, FooterMetricSetting> metricSettings(FooterConfigView config) {
        Map<String, FooterMetricSetting> settings = new LinkedHashMap<>();
        if (config.performanceMetrics() != null) {
            for (FooterMetricSetting setting : config.performanceMetrics()) {
                if (setting != null) settings.put(setting.key(), setting);
            }
        }
        return settings;
    }

    private FooterPerformanceView redactPerformance(
            FooterPerformanceView performance,
            Map<String, FooterMetricSetting> settings,
            boolean authenticated,
            boolean admin
    ) {
        HttpResponsePerformanceView http = performance.httpResponses();
        return new FooterPerformanceView(
                metric(performance.loadAverage1(), metricVisible(
                        settings, "loadAverage", authenticated, admin)),
                metric(performance.loadAverage5(), metricVisible(
                        settings, "loadAverage", authenticated, admin)),
                metric(performance.loadAverage15(), metricVisible(
                        settings, "loadAverage", authenticated, admin)),
                metric(performance.cpuPercent(), metricVisible(
                        settings, "cpu", authenticated, admin)),
                metric(performance.memoryPercent(), metricVisible(
                        settings, "memory", authenticated, admin)),
                metric(performance.systemUptimeSeconds(), metricVisible(
                        settings, "systemUptime", authenticated, admin)),
                metric(performance.serverUptimeSeconds(), metricVisible(
                        settings, "serverUptime", authenticated, admin)),
                metric(performance.diskIoReadBytesPerSecond(), metricVisible(
                        settings, "diskIo", authenticated, admin)),
                metric(performance.diskIoWriteBytesPerSecond(), metricVisible(
                        settings, "diskIo", authenticated, admin)),
                metric(performance.networkReceiveBytesPerSecond(), metricVisible(
                        settings, "network", authenticated, admin)),
                metric(performance.networkTransmitBytesPerSecond(), metricVisible(
                        settings, "network", authenticated, admin)),
                metric(performance.httpRequestsPerSecond(), metricVisible(
                        settings, "httpRps", authenticated, admin)),
                metric(performance.sqlQueriesPerSecond(), metricVisible(
                        settings, "sqlQps", authenticated, admin)),
                metric(performance.redisQueriesPerSecond(), metricVisible(
                        settings, "redisQps", authenticated, admin)),
                http == null ? null : new HttpResponsePerformanceView(
                        metric(http.apiAverageMillis(), metricVisible(
                                settings, "httpApiAverage", authenticated, admin)),
                        metric(http.apiP99Millis(), metricVisible(
                                settings, "httpApiP99", authenticated, admin)),
                        metric(http.btnPingAverageMillis(), metricVisible(
                                settings, "httpPingAverage", authenticated, admin)),
                        metric(http.btnPingP99Millis(), metricVisible(
                                settings, "httpPingP99", authenticated, admin)),
                        metric(http.otherAverageMillis(), metricVisible(
                                settings, "httpOtherAverage", authenticated, admin)),
                        metric(http.otherP99Millis(), metricVisible(
                                settings, "httpOtherP99", authenticated, admin))
                )
        );
    }

    private boolean metricVisible(Map<String, FooterMetricSetting> settings,
                                  String key,
                                  boolean authenticated,
                                  boolean admin) {
        FooterMetricSetting setting = settings.get(key);
        return setting != null
                && setting.enabled()
                && canView(setting.audience(), authenticated, admin);
    }

    private <T> T metric(T value, boolean visible) {
        return visible ? value : null;
    }

    private boolean canView(FooterAudience audience,
                            boolean authenticated,
                            boolean admin) {
        if (audience == null) return false;
        return switch (audience) {
            case AUTHENTICATED -> authenticated;
            case ADMIN -> admin;
        };
    }

}
