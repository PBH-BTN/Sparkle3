package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterAudience;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterColumnView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterLinkView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterMetricSetting;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FooterConfigService {
    public static final String CONFIG_KEY = "sparkle.footer.config";
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final int MAX_COLUMNS = 12;
    private static final int MAX_LINKS_PER_COLUMN = 30;
    private static final String DEFAULT_DISCLAIMER_ZH = """
            Sparkle BTN 是由 PBH-BTN 运营和维护的 BitTorrent 威胁情报网络。
            使用 BTN 服务的用户自愿运行 BTN 客户端软件，并为此网络贡献数据。
            此网络将为如 BTN 客户端防火墙软件提供重要防护数据，用于抵御互联网恶意攻击。
            Sparkle BTN 并不是 BT Tracker 或 BT 客户端，因此不辅助进行用户连接。
            也不参与存储、传输、共享任何 BitTorrent 流量、数据、资料或者 Info Hash。""";
    private static final String DEFAULT_DISCLAIMER_EN = """
            Sparkle BTN is a BitTorrent threat intelligence network operated and maintained by PBH-BTN.
            Users of BTN services voluntarily run BTN client software and contribute data to this network.
            The network provides essential protection data to applications such as BTN client firewalls, helping defend against malicious internet activity.
            Sparkle BTN is not a BitTorrent tracker or client and therefore does not facilitate user connections.
            It does not store, transmit, or share any BitTorrent traffic, data, material, or Info Hash.""";

    public enum Metric {
        LOAD_AVERAGE("loadAverage"),
        CPU("cpu"),
        MEMORY("memory"),
        SYSTEM_UPTIME("systemUptime"),
        SERVER_UPTIME("serverUptime"),
        DISK_IO("diskIo"),
        NETWORK("network"),
        HTTP_RPS("httpRps"),
        SQL_QPS("sqlQps"),
        REDIS_QPS("redisQps"),
        HTTP_API_AVERAGE("httpApiAverage"),
        HTTP_API_P99("httpApiP99"),
        HTTP_PING_AVERAGE("httpPingAverage"),
        HTTP_PING_P99("httpPingP99"),
        HTTP_OTHER_AVERAGE("httpOtherAverage"),
        HTTP_OTHER_P99("httpOtherP99");

        private final String key;

        Metric(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private final DatabaseConfigService databaseConfigService;
    private final ObjectMapper objectMapper;
    private final FooterConfigView defaults;
    private volatile FooterConfigView cached;
    private volatile long cacheExpiresAt;
    private volatile boolean defaultStored;

    public FooterConfigService(DatabaseConfigService databaseConfigService, ObjectMapper objectMapper) {
        this.databaseConfigService = databaseConfigService;
        this.objectMapper = objectMapper;
        List<FooterMetricSetting> metricSettings = new ArrayList<>();
        for (Metric metric : Metric.values()) {
            metricSettings.add(new FooterMetricSetting(metric.key(), true, FooterAudience.ADMIN));
        }
        this.defaults = new FooterConfigView(
                DEFAULT_DISCLAIMER_ZH,
                DEFAULT_DISCLAIMER_EN,
                FooterAudience.AUTHENTICATED,
                "",
                FooterAudience.AUTHENTICATED,
                List.of(),
                FooterAudience.AUTHENTICATED,
                FooterAudience.AUTHENTICATED,
                FooterAudience.AUTHENTICATED,
                List.copyOf(metricSettings)
        );
    }

    public @NotNull FooterConfigView get() {
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

    @Transactional
    public @NotNull FooterConfigView update(@NotNull FooterConfigView config,
                                            @Nullable Long operatorId) throws BusinessException {
        FooterConfigView normalized = normalize(config);
        ensureDefaultStored();
        databaseConfigService.update(CONFIG_KEY, serialize(normalized), operatorId);
        cached = normalized;
        cacheExpiresAt = System.currentTimeMillis() + CACHE_TTL.toMillis();
        return normalized;
    }

    private FooterConfigView load() {
        try {
            ensureDefaultStored();
            String json = databaseConfigService.getRawValues(List.of(CONFIG_KEY)).get(CONFIG_KEY);
            if (json == null || json.isBlank()) {
                return defaults;
            }
            return normalize(objectMapper.readValue(json, FooterConfigView.class));
        } catch (Exception exception) {
            return cached == null ? defaults : cached;
        }
    }

    private void ensureDefaultStored() throws BusinessException {
        if (defaultStored) {
            return;
        }
        synchronized (this) {
            if (defaultStored) {
                return;
            }
            databaseConfigService.insertIfMissing(CONFIG_KEY, serialize(defaults), "yaml");
            defaultStored = true;
        }
    }

    private FooterConfigView normalize(FooterConfigView config) throws BusinessException {
        if (config == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Footer configuration is required");
        }
        String disclaimerZh = bounded(config.disclaimerZh(), 20_000, "Chinese disclaimer");
        String disclaimerEn = bounded(config.disclaimerEn(), 20_000, "English disclaimer");
        String icp = bounded(config.icp(), 128, "ICP filing number");
        List<FooterColumnView> columns = normalizeColumns(config.columns());
        return new FooterConfigView(
                disclaimerZh,
                disclaimerEn,
                audience(config.disclaimerAudience(), FooterAudience.AUTHENTICATED),
                icp,
                audience(config.icpAudience(), FooterAudience.AUTHENTICATED),
                columns,
                audience(config.columnsAudience(), FooterAudience.AUTHENTICATED),
                audience(config.timeAudience(), FooterAudience.AUTHENTICATED),
                audience(config.loginAudience(), FooterAudience.AUTHENTICATED),
                normalizeMetricSettings(config.performanceMetrics())
        );
    }

    private List<FooterColumnView> normalizeColumns(List<FooterColumnView> columns)
            throws BusinessException {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "At most " + MAX_COLUMNS + " footer columns are allowed");
        }
        List<FooterColumnView> normalized = new ArrayList<>(columns.size());
        for (FooterColumnView column : columns) {
            if (column == null) continue;
            String titleZh = bounded(column.titleZh(), 100, "Chinese column title");
            String titleEn = bounded(column.titleEn(), 100, "English column title");
            if (titleZh.isBlank() && titleEn.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "A footer column must have a Chinese or English title");
            }
            List<FooterLinkView> links = normalizeLinks(column.links());
            normalized.add(new FooterColumnView(titleZh, titleEn, links));
        }
        return List.copyOf(normalized);
    }

    private List<FooterLinkView> normalizeLinks(List<FooterLinkView> links)
            throws BusinessException {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        if (links.size() > MAX_LINKS_PER_COLUMN) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "At most " + MAX_LINKS_PER_COLUMN + " links are allowed per footer column");
        }
        List<FooterLinkView> normalized = new ArrayList<>(links.size());
        for (FooterLinkView link : links) {
            if (link == null) continue;
            String labelZh = bounded(link.labelZh(), 100, "Chinese link label");
            String labelEn = bounded(link.labelEn(), 100, "English link label");
            String href = bounded(link.href(), 2_048, "Footer link");
            if (labelZh.isBlank() && labelEn.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "A footer link must have a Chinese or English label");
            }
            validateHref(href);
            normalized.add(new FooterLinkView(labelZh, labelEn, href));
        }
        return List.copyOf(normalized);
    }

    private List<FooterMetricSetting> normalizeMetricSettings(List<FooterMetricSetting> settings) {
        Map<String, FooterMetricSetting> provided = new LinkedHashMap<>();
        if (settings != null) {
            for (FooterMetricSetting setting : settings) {
                if (setting == null || setting.key() == null) continue;
                provided.put(setting.key(), setting);
            }
        }
        List<FooterMetricSetting> normalized = new ArrayList<>(Metric.values().length);
        for (Metric metric : Metric.values()) {
            FooterMetricSetting setting = provided.get(metric.key());
            normalized.add(new FooterMetricSetting(
                    metric.key(),
                    setting == null || setting.enabled(),
                    setting == null
                            ? FooterAudience.ADMIN
                            : audience(setting.audience(), FooterAudience.ADMIN)
            ));
        }
        return List.copyOf(normalized);
    }

    private FooterAudience audience(FooterAudience audience, FooterAudience fallback) {
        return audience == null ? fallback : audience;
    }

    private String bounded(String value, int maxLength, String fieldName) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private void validateHref(String href) throws BusinessException {
        if (href.isBlank() || href.startsWith("//")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Footer links must use an internal path or an HTTP(S) URL");
        }
        if (href.startsWith("/")) {
            return;
        }
        try {
            URI uri = URI.create(href);
            if (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Unsupported scheme");
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Footer links must use an internal path or an HTTP(S) URL");
        }
    }

    private String serialize(FooterConfigView config) throws BusinessException {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize footer configuration");
        }
    }
}
