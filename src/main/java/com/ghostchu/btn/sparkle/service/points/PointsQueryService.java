package com.ghostchu.btn.sparkle.service.points;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.CategoryPoints;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.DynamicPointsSummary;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.FeatureFlags;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.FixedPointsSummary;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsLogEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsLogPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsOverview;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.mapper.PointsMapper;
import com.ghostchu.btn.sparkle.util.JdbcTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsQueryService {
    private static final List<PointCategory> FIXED_CATEGORIES = List.of(
            PointCategory.LOGIN,
            PointCategory.ACTIVE_APP,
            PointCategory.APP_ONLINE,
            PointCategory.BAN_HISTORY,
            PointCategory.SWARM_TRACKER,
            PointCategory.RULES_FETCH,
            PointCategory.KEEPALIVE,
            PointCategory.RULE_HIT_IP,
            PointCategory.WASTED_UPLOAD
    );

    private final PointsMapper pointsMapper;
    private final IUserService userService;
    private final AnalyseModuleConfigService moduleConfigService;
    private final PointToggleService pointToggleService;
    private final PointRulesService pointRulesService;
    private final ObjectMapper objectMapper;
    private final BtnRuntimeConfigService btnConfigService;

    public PointsOverview overview(long userId) {
        ZoneId zone = ZoneId.of(btnConfigService.text("points", "timeZone"));
        ZonedDateTime now = ZonedDateTime.now(zone);
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime last24Hours = now.minusHours(24).toOffsetDateTime();
        LocalDate pointDay = now.toLocalDate();

        var user = userService.getById(userId);
        long balance = user == null || user.getScoreBytes() == null
                ? 0
                : user.getScoreBytes();
        Map<PointCategory, Long> categories = loadCategories(userId, pointDay);
        Map<String, Object> aggregateRow = pointsMapper.selectAggregate(
                userId,
                dayStart,
                last24Hours
        );
        PointAggregate aggregate = new PointAggregate(
                longValue(aggregateRow, "fixed_today"),
                longValue(aggregateRow, "dynamic_today"),
                longValue(aggregateRow, "fixed_24h"),
                longValue(aggregateRow, "dynamic_24h")
        );

        boolean dynamicEnabled = moduleConfigService.isEnabled("points-dynamic");
        boolean swarmInsightsEnabled = moduleConfigService.isEnabled("swarm-peer-distribution");
        boolean banInsightsEnabled = moduleConfigService.isEnabled("ban-peer-distribution");

        List<CategoryPoints> fixedCategories = FIXED_CATEGORIES.stream()
                .map(category -> new CategoryPoints(
                        category.name(),
                        Long.toString(categories.getOrDefault(category, 0L)),
                        pointRulesService.dailyCap(category) == null
                                ? null
                                : pointRulesService.dailyCap(category).toString(),
                        pointToggleService.isEnabled(category)
                ))
                .toList();
        long cappedPoints = categories.getOrDefault(PointCategory.LOGIN, 0L)
                + categories.getOrDefault(PointCategory.BAN_HISTORY, 0L)
                + categories.getOrDefault(PointCategory.SWARM_TRACKER, 0L)
                + categories.getOrDefault(PointCategory.RULES_FETCH, 0L)
                + categories.getOrDefault(PointCategory.KEEPALIVE, 0L)
                + categories.getOrDefault(PointCategory.RULE_HIT_IP, 0L)
                + categories.getOrDefault(PointCategory.WASTED_UPLOAD, 0L);
        long fixedDailyCap = FIXED_CATEGORIES.stream()
                .map(pointRulesService::dailyCap)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        var fixed = new FixedPointsSummary(
                Long.toString(aggregate.fixedToday()),
                Long.toString(cappedPoints),
                Long.toString(fixedDailyCap),
                averagePerHour(aggregate.fixed24h()),
                fixedCategories
        );
        DynamicPointsSummary dynamic = dynamicEnabled
                && pointToggleService.isAnyDynamicEnabled()
                ? new DynamicPointsSummary(
                        Long.toString(aggregate.dynamicToday()),
                        averagePerHour(aggregate.dynamic24h()),
                        Long.toString(categories.getOrDefault(
                                PointCategory.DYNAMIC_SEED_UPLOAD, 0L)),
                        Long.toString(categories.getOrDefault(
                                PointCategory.DYNAMIC_DOWNLOAD, 0L)),
                        Long.toString(categories.getOrDefault(
                                PointCategory.DYNAMIC_HOT_PEER, 0L)),
                        pointToggleService.isEnabled(PointCategory.DYNAMIC_SEED_UPLOAD),
                        pointToggleService.isEnabled(PointCategory.DYNAMIC_DOWNLOAD),
                        pointToggleService.isEnabled(PointCategory.DYNAMIC_HOT_PEER)
                )
                : null;

        return new PointsOverview(
                Long.toString(balance),
                fixed,
                dynamic,
                new FeatureFlags(
                        dynamicEnabled && pointToggleService.isAnyDynamicEnabled(),
                        swarmInsightsEnabled,
                        banInsightsEnabled
                ),
                OffsetDateTime.now()
        );
    }

    public PointsLogPage logs(long userId, int requestedPage, int requestedSize) {
        int current = Math.max(1, requestedPage);
        int size = Math.min(100, Math.max(1, requestedSize));
        long total = pointsMapper.countLedger(userId);
        List<PointsLogEntry> entries = pointsMapper.selectLedger(
                userId,
                size,
                (long) (current - 1) * size
        ).stream().map(row -> new PointsLogEntry(
                longValue(row, "id"),
                JdbcTime.toOffsetDateTime(row.get("occurred_at")),
                String.valueOf(row.get("event_type")),
                Long.toString(longValue(row, "points")),
                Long.toString(longValue(row, "fixed_points")),
                Long.toString(longValue(row, "dynamic_points")),
                Long.toString(longValue(row, "manual_points")),
                readDetails(String.valueOf(row.get("details")))
        )).toList();
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new PointsLogPage(entries, current, size, total, pages);
    }

    private Map<PointCategory, Long> loadCategories(long userId, LocalDate pointDay) {
        Map<PointCategory, Long> result = new EnumMap<>(PointCategory.class);
        pointsMapper.selectDailyCategories(userId, pointDay).forEach(row -> {
            try {
                result.put(
                        PointCategory.valueOf(String.valueOf(row.get("category"))),
                        longValue(row, "awarded")
                );
            } catch (IllegalArgumentException ignored) {
                // Ignore categories removed in a newer application version.
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDetails(String json) {
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception exception) {
            log.warn("Unable to parse points event details", exception);
            return Map.of();
        }
    }

    private long averagePerHour(long total) {
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(24), 0, RoundingMode.DOWN)
                .longValue();
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private record PointAggregate(
            long fixedToday,
            long dynamicToday,
            long fixed24h,
            long dynamic24h
    ) {
    }
}
