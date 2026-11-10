package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.NavigationItemInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.NavigationItemView;
import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.PublicNavigationItem;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class NavigationService {
    private static final String RANKING_ID = "builtin-ranking";
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final Set<String> ICONS = Set.of(
            "home", "apps", "ban", "search", "swarm", "ranking",
            "link", "file", "dashboard"
    );
    private static final RowMapper<NavigationItemView> ROW_MAPPER = (resultSet, rowNum) ->
            new NavigationItemView(
                    resultSet.getString("id"),
                    resultSet.getBoolean("built_in"),
                    resultSet.getString("label_zh"),
                    resultSet.getString("label_en"),
                    resultSet.getString("href"),
                    resultSet.getString("icon"),
                    resultSet.getInt("sort_order"),
                    resultSet.getBoolean("enabled"),
                    resultSet.getBoolean("open_in_new_tab"),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("updated_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("updated_by", Long.class)
            );

    private final JdbcTemplate jdbcTemplate;
    private final AdminConfigService adminConfigService;
    private volatile List<NavigationItemView> cached;
    private volatile long cacheExpiresAt;
    private volatile boolean schemaReady;

    public NavigationService(JdbcTemplate jdbcTemplate, AdminConfigService adminConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminConfigService = adminConfigService;
    }

    public @NotNull List<NavigationItemView> listAdmin() {
        return loadAll();
    }

    public @NotNull List<PublicNavigationItem> listPublic() {
        boolean statisticsEnabled = adminConfigService.get().swarmStatisticsEnabled();
        return loadAll().stream()
                .filter(NavigationItemView::enabled)
                .filter(item -> statisticsEnabled || !RANKING_ID.equals(item.id()))
                .map(item -> new PublicNavigationItem(
                        item.id(),
                        item.labelZh(),
                        item.labelEn(),
                        item.href(),
                        item.icon(),
                        item.openInNewTab()
                ))
                .toList();
    }

    public @NotNull NavigationItemView create(
            @NotNull NavigationItemInput input,
            @Nullable Long operatorId
    ) throws BusinessException {
        NavigationItemInput normalized = validate(input);
        ensureSchema();
        String id = "custom-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO navigation_items (
                    id, built_in, label_zh, label_en, href, icon,
                    sort_order, enabled, open_in_new_tab, updated_by
                )
                VALUES (?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                normalized.labelZh(),
                normalized.labelEn(),
                normalized.href(),
                normalized.icon(),
                normalized.sortOrder(),
                normalized.enabled(),
                normalized.openInNewTab(),
                operatorId
        );
        invalidateCache();
        return require(id);
    }

    public @NotNull NavigationItemView update(
            @NotNull String id,
            @NotNull NavigationItemInput input,
            @Nullable Long operatorId
    ) throws BusinessException {
        NavigationItemView existing = require(id);
        NavigationItemInput normalized = validate(input);
        String href = existing.builtIn() ? existing.href() : normalized.href();
        int changed = jdbcTemplate.update("""
                UPDATE navigation_items
                SET label_zh = ?,
                    label_en = ?,
                    href = ?,
                    icon = ?,
                    sort_order = ?,
                    enabled = ?,
                    open_in_new_tab = ?,
                    updated_at = now(),
                    updated_by = ?
                WHERE id = ?
                """,
                normalized.labelZh(),
                normalized.labelEn(),
                href,
                normalized.icon(),
                normalized.sortOrder(),
                normalized.enabled(),
                normalized.openInNewTab(),
                operatorId,
                id
        );
        if (changed == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Navigation item not found");
        }
        invalidateCache();
        return require(id);
    }

    public void delete(@NotNull String id) throws BusinessException {
        NavigationItemView existing = require(id);
        if (existing.builtIn()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Built-in navigation items cannot be deleted");
        }
        jdbcTemplate.update("DELETE FROM navigation_items WHERE id = ?", id);
        invalidateCache();
    }

    private List<NavigationItemView> loadAll() {
        long now = System.currentTimeMillis();
        if (cached != null && now < cacheExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (cached != null && now < cacheExpiresAt) {
                return cached;
            }
            ensureSchema();
            cached = jdbcTemplate.query("""
                    SELECT *
                    FROM navigation_items
                    ORDER BY sort_order, created_at, id
                    """, ROW_MAPPER);
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return cached;
        }
    }

    private NavigationItemView require(String id) throws BusinessException {
        ensureSchema();
        List<NavigationItemView> items = jdbcTemplate.query(
                "SELECT * FROM navigation_items WHERE id = ?",
                ROW_MAPPER,
                id
        );
        if (items.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Navigation item not found");
        }
        return items.getFirst();
    }

    private NavigationItemInput validate(NavigationItemInput input) throws BusinessException {
        String labelZh = normalize(input.labelZh());
        String labelEn = normalize(input.labelEn());
        String href = normalize(input.href());
        String icon = normalize(input.icon()).toLowerCase(Locale.ROOT);
        if (labelZh.isBlank() || labelEn.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Chinese and English navigation labels are required");
        }
        if (labelZh.length() > 100 || labelEn.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Navigation labels must not exceed 100 characters");
        }
        if (href.isBlank() || href.length() > 2048) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Navigation target is required and must not exceed 2048 characters");
        }
        if (href.startsWith("//")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Protocol-relative navigation targets are not supported");
        }
        if (!href.startsWith("/")) {
            try {
                URI uri = URI.create(href);
                if (!"http".equalsIgnoreCase(uri.getScheme())
                        && !"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new IllegalArgumentException("Unsupported scheme");
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Custom navigation target must be an internal path or HTTP(S) URL");
            }
        }
        if (!ICONS.contains(icon)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported navigation icon");
        }
        if (input.sortOrder() < -10_000 || input.sortOrder() > 10_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid navigation sort order");
        }
        return new NavigationItemInput(
                labelZh,
                labelEn,
                href,
                icon,
                input.sortOrder(),
                input.enabled(),
                input.openInNewTab()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS navigation_items (
                        id VARCHAR(128) PRIMARY KEY,
                        built_in BOOLEAN NOT NULL DEFAULT FALSE,
                        label_zh VARCHAR(100) NOT NULL,
                        label_en VARCHAR(100) NOT NULL,
                        href VARCHAR(2048) NOT NULL,
                        icon VARCHAR(32) NOT NULL DEFAULT 'link',
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        open_in_new_tab BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_by BIGINT
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS navigation_items_order_idx
                    ON navigation_items (enabled, sort_order)
                    """);
            insertDefaults();
            schemaReady = true;
        }
    }

    private void insertDefaults() {
        Map<String, NavigationItemInput> defaults = new LinkedHashMap<>();
        defaults.put("builtin-home", defaultItem("首页", "Home", "/", "home", 10));
        defaults.put("builtin-userapps", defaultItem("用户应用", "Applications", "/userapp", "apps", 20));
        defaults.put("builtin-banhistory", defaultItem("封禁记录", "Ban history", "/banhistory", "ban", 30));
        defaults.put("builtin-client-discovery",
                defaultItem("特征发现", "Client discovery", "/client-discovery", "search", 40));
        defaults.put("builtin-swarm-tracker",
                defaultItem("种群检视", "Swarm explorer", "/swarm-tracker", "swarm", 50));
        defaults.put("builtin-ranking",
                defaultItem("排行榜", "Ranking", "/ranking/swarmStatistics", "ranking", 60));
        for (Map.Entry<String, NavigationItemInput> entry : defaults.entrySet()) {
            NavigationItemInput item = entry.getValue();
            jdbcTemplate.update("""
                    INSERT INTO navigation_items (
                        id, built_in, label_zh, label_en, href, icon,
                        sort_order, enabled, open_in_new_tab
                    )
                    VALUES (?, TRUE, ?, ?, ?, ?, ?, TRUE, FALSE)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    entry.getKey(),
                    item.labelZh(),
                    item.labelEn(),
                    item.href(),
                    item.icon(),
                    item.sortOrder()
            );
        }
    }

    private NavigationItemInput defaultItem(
            String labelZh,
            String labelEn,
            String href,
            String icon,
            int sortOrder
    ) {
        return new NavigationItemInput(
                labelZh,
                labelEn,
                href,
                icon,
                sortOrder,
                true,
                false
        );
    }

    private void invalidateCache() {
        cached = null;
        cacheExpiresAt = 0;
    }
}
