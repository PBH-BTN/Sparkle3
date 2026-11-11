package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.util.List;

public final class FooterModels {
    private FooterModels() {
    }

    public enum FooterAudience {
        AUTHENTICATED,
        ADMIN
    }

    public record FooterLinkView(
            String labelZh,
            String labelEn,
            String href
    ) {
    }

    public record FooterColumnView(
            String titleZh,
            String titleEn,
            List<FooterLinkView> links
    ) {
    }

    public record FooterMetricSetting(
            String key,
            boolean enabled,
            FooterAudience audience
    ) {
    }

    public record FooterConfigView(
            String disclaimerZh,
            String disclaimerEn,
            FooterAudience disclaimerAudience,
            String icp,
            FooterAudience icpAudience,
            List<FooterColumnView> columns,
            FooterAudience columnsAudience,
            FooterAudience timeAudience,
            FooterAudience loginAudience,
            List<FooterMetricSetting> performanceMetrics
    ) {
    }

    public record FooterPerformanceView(
            Double loadAverage,
            Double cpuPercent,
            Double memoryPercent,
            Long systemUptimeSeconds,
            Long serverUptimeSeconds,
            Double diskIoBytesPerSecond,
            Double networkBytesPerSecond,
            Double httpRequestsPerSecond,
            Double sqlQueriesPerSecond,
            Double redisQueriesPerSecond,
            HttpResponsePerformanceView httpResponses
    ) {
    }

    public record HttpResponsePerformanceView(
            Double apiAverageMillis,
            Double apiP99Millis,
            Double btnPingAverageMillis,
            Double btnPingP99Millis,
            Double otherAverageMillis,
            Double otherP99Millis
    ) {
    }

    public record WorldTimeView(
            long serverEpochMillis,
            String serverTimeZone
    ) {
    }

    public enum ProxyProvider {
        CLOUDFLARE,
        TENCENT_EDGEONE
    }

    public record VisitorView(
            String ip,
            String geo,
            String system,
            String browser,
            ProxyProvider proxyProvider
    ) {
    }

    public record FooterResponse(
            String disclaimerZh,
            String disclaimerEn,
            String icp,
            List<FooterColumnView> columns,
            WorldTimeView worldTime,
            FooterPerformanceView performance,
            VisitorView visitor
    ) {
    }
}
