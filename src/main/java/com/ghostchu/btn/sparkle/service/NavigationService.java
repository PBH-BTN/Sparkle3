package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.NavigationItemInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.NavigationItemView;
import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.PublicNavigationItem;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.entity.NavigationItem;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.NavigationItemMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
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
            "rules", "link", "file", "dashboard"
    );
    private final NavigationItemMapper navigationMapper;
    private final AdminConfigService adminConfigService;
    private volatile List<NavigationItemView> cached;
    private volatile long cacheExpiresAt;
    private volatile boolean defaultsReady;

    public NavigationService(NavigationItemMapper navigationMapper, AdminConfigService adminConfigService) {
        this.navigationMapper = navigationMapper;
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
        ensureDefaults();
        String id = "custom-" + UUID.randomUUID();
        navigationMapper.insert(new NavigationItem()
                .setId(id)
                .setBuiltIn(false)
                .setLabelZh(normalized.labelZh())
                .setLabelEn(normalized.labelEn())
                .setHref(normalized.href())
                .setIcon(normalized.icon())
                .setSortOrder(normalized.sortOrder())
                .setEnabled(normalized.enabled())
                .setOpenInNewTab(normalized.openInNewTab())
                .setUpdatedBy(operatorId));
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
        NavigationItem entity = navigationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Navigation item not found");
        }
        entity.setLabelZh(normalized.labelZh())
                .setLabelEn(normalized.labelEn())
                .setHref(href)
                .setIcon(normalized.icon())
                .setSortOrder(normalized.sortOrder())
                .setEnabled(normalized.enabled())
                .setOpenInNewTab(normalized.openInNewTab())
                .setUpdatedAt(java.time.OffsetDateTime.now())
                .setUpdatedBy(operatorId);
        navigationMapper.updateById(entity);
        invalidateCache();
        return require(id);
    }

    public void delete(@NotNull String id) throws BusinessException {
        NavigationItemView existing = require(id);
        if (existing.builtIn()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Built-in navigation items cannot be deleted");
        }
        navigationMapper.deleteById(id);
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
            ensureDefaults();
            cached = navigationMapper.selectList(
                    new QueryWrapper<NavigationItem>()
                            .orderByAsc("sort_order")
                            .orderByAsc("created_at")
                            .orderByAsc("id")
            ).stream().map(this::toView).toList();
            cacheExpiresAt = now + CACHE_TTL.toMillis();
            return cached;
        }
    }

    private NavigationItemView require(String id) throws BusinessException {
        ensureDefaults();
        NavigationItem item = navigationMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Navigation item not found");
        }
        return toView(item);
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

    private void ensureDefaults() {
        if (defaultsReady) {
            return;
        }
        synchronized (this) {
            if (defaultsReady) {
                return;
            }
            insertDefaults();
            defaultsReady = true;
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
        defaults.put("builtin-rule-preview",
                defaultItem("规则预览", "Rule preview", "/rules/preview", "rules", 55));
        defaults.put("builtin-ranking",
                defaultItem("排行榜", "Ranking", "/ranking/swarmStatistics", "ranking", 60));
        for (Map.Entry<String, NavigationItemInput> entry : defaults.entrySet()) {
            NavigationItemInput item = entry.getValue();
            if (navigationMapper.selectById(entry.getKey()) == null) {
                navigationMapper.insert(new NavigationItem()
                        .setId(entry.getKey())
                        .setBuiltIn(true)
                        .setLabelZh(item.labelZh())
                        .setLabelEn(item.labelEn())
                        .setHref(item.href())
                        .setIcon(item.icon())
                        .setSortOrder(item.sortOrder())
                        .setEnabled(true)
                        .setOpenInNewTab(false));
            }
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

    private NavigationItemView toView(NavigationItem item) {
        return new NavigationItemView(
                item.getId(),
                Boolean.TRUE.equals(item.getBuiltIn()),
                item.getLabelZh(),
                item.getLabelEn(),
                item.getHref(),
                item.getIcon(),
                item.getSortOrder() == null ? 0 : item.getSortOrder(),
                Boolean.TRUE.equals(item.getEnabled()),
                Boolean.TRUE.equals(item.getOpenInNewTab()),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getUpdatedBy()
        );
    }
}
