package com.ghostchu.btn.sparkle.service.points;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.CategoryPoints;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.DynamicPointsSummary;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.FeatureFlags;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.FixedPointsSummary;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.OneTimePointsSummary;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsLogEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsLogPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsOverview;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import com.ghostchu.btn.sparkle.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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
            PointCategory.NEW_IP,
            PointCategory.WASTED_UPLOAD
    );

    private final JdbcTemplate jdbcTemplate;
    private final IUserService userService;
    private final AnalyseModuleConfigService moduleConfigService;
    private final PointToggleService pointToggleService;
    private final ObjectMapper objectMapper;

    @Value("${sparkle.points.time-zone:Asia/Shanghai}")
    private String pointTimeZone = "Asia/Shanghai";

    public PointsOverview overview(long userId) {
        ZoneId zone = ZoneId.of(pointTimeZone);
        ZonedDateTime now = ZonedDateTime.now(zone);
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime last24Hours = now.minusHours(24).toOffsetDateTime();
        LocalDate pointDay = now.toLocalDate();

        var user = userService.getById(userId);
        long balance = user == null || user.getScoreBytes() == null
                ? 0
                : user.getScoreBytes();
        Map<PointCategory, Long> categories = loadCategories(userId, pointDay);
        PointAggregate aggregate = jdbcTemplate.queryForObject(
                """
                SELECT
                    COALESCE(SUM(fixed_points) FILTER (WHERE occurred_at >= ?), 0) AS fixed_today,
                    COALESCE(SUM(one_time_points) FILTER (WHERE occurred_at >= ?), 0) AS once_today,
                    COALESCE(SUM(dynamic_points) FILTER (WHERE occurred_at >= ?), 0) AS dynamic_today,
                    COALESCE(SUM(fixed_points) FILTER (WHERE occurred_at >= ?), 0) AS fixed_24h,
                    COALESCE(SUM(one_time_points) FILTER (WHERE occurred_at >= ?), 0) AS once_24h,
                    COALESCE(SUM(dynamic_points) FILTER (WHERE occurred_at >= ?), 0) AS dynamic_24h
                FROM public.points_ledger
                WHERE user_id = ?
                  AND occurred_at >= ?
                """,
                (resultSet, rowNum) -> new PointAggregate(
                        resultSet.getLong("fixed_today"),
                        resultSet.getLong("once_today"),
                        resultSet.getLong("dynamic_today"),
                        resultSet.getLong("fixed_24h"),
                        resultSet.getLong("once_24h"),
                        resultSet.getLong("dynamic_24h")
                ),
                dayStart,
                dayStart,
                dayStart,
                last24Hours,
                last24Hours,
                last24Hours,
                userId,
                last24Hours
        );

        boolean dynamicEnabled = moduleConfigService.isEnabled("points-dynamic");
        boolean swarmInsightsEnabled = moduleConfigService.isEnabled("swarm-peer-distribution");
        boolean banInsightsEnabled = moduleConfigService.isEnabled("ban-peer-distribution");

        List<CategoryPoints> fixedCategories = FIXED_CATEGORIES.stream()
                .map(category -> new CategoryPoints(
                        category.name(),
                        Long.toString(categories.getOrDefault(category, 0L)),
                        category.getDailyCap() == null
                                ? null
                                : Long.toString(category.getDailyCap()),
                        pointToggleService.isEnabled(category)
                ))
                .toList();
        long cappedPoints = categories.getOrDefault(PointCategory.BAN_HISTORY, 0L)
                + categories.getOrDefault(PointCategory.SWARM_TRACKER, 0L)
                + categories.getOrDefault(PointCategory.RULES_FETCH, 0L)
                + categories.getOrDefault(PointCategory.KEEPALIVE, 0L)
                + categories.getOrDefault(PointCategory.RULE_HIT_IP, 0L)
                + categories.getOrDefault(PointCategory.NEW_IP, 0L)
                + categories.getOrDefault(PointCategory.WASTED_UPLOAD, 0L);
        long fixedDailyCap = FIXED_CATEGORIES.stream()
                .map(PointCategory::getDailyCap)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long newTorrents = categories.getOrDefault(PointCategory.NEW_TORRENT, 0L) / 500L;
        long discoveries = categories.getOrDefault(PointCategory.CLIENT_DISCOVERY, 0L) / 4L;

        var fixed = new FixedPointsSummary(
                Long.toString(aggregate.fixedToday()),
                Long.toString(cappedPoints),
                Long.toString(fixedDailyCap),
                averagePerHour(aggregate.fixed24h()),
                fixedCategories
        );
        var oneTime = new OneTimePointsSummary(
                Long.toString(aggregate.onceToday()),
                averagePerHour(aggregate.once24h()),
                newTorrents,
                discoveries,
                pointToggleService.isEnabled(PointCategory.NEW_TORRENT),
                pointToggleService.isEnabled(PointCategory.CLIENT_DISCOVERY)
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
                oneTime,
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
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.points_ledger WHERE user_id = ?",
                Long.class,
                userId
        );
        List<PointsLogEntry> entries = jdbcTemplate.query(
                """
                SELECT id, occurred_at, event_type, points, fixed_points,
                       one_time_points, dynamic_points, details::text AS details
                FROM public.points_ledger
                WHERE user_id = ?
                ORDER BY occurred_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, rowNum) -> new PointsLogEntry(
                        resultSet.getLong("id"),
                        resultSet.getObject("occurred_at", OffsetDateTime.class),
                        resultSet.getString("event_type"),
                        Long.toString(resultSet.getLong("points")),
                        Long.toString(resultSet.getLong("fixed_points")),
                        Long.toString(resultSet.getLong("one_time_points")),
                        Long.toString(resultSet.getLong("dynamic_points")),
                        readDetails(resultSet.getString("details"))
                ),
                userId,
                size,
                (long) (current - 1) * size
        );
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new PointsLogPage(entries, current, size, total, pages);
    }

    private Map<PointCategory, Long> loadCategories(long userId, LocalDate pointDay) {
        Map<PointCategory, Long> result = new EnumMap<>(PointCategory.class);
        jdbcTemplate.query(
                """
                SELECT category, awarded
                FROM public.point_daily_category
                WHERE user_id = ? AND point_day = ?
                """,
                resultSet -> {
                    try {
                        result.put(
                                PointCategory.valueOf(resultSet.getString("category")),
                                resultSet.getLong("awarded")
                        );
                    } catch (IllegalArgumentException ignored) {
                        // Ignore categories removed in a newer application version.
                    }
                },
                userId,
                pointDay
        );
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

    private record PointAggregate(
            long fixedToday,
            long onceToday,
            long dynamicToday,
            long fixed24h,
            long once24h,
            long dynamic24h
    ) {
    }
}
