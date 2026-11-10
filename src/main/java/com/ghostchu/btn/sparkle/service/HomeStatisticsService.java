package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.HomeStatisticsModels.ChartDatum;
import com.ghostchu.btn.sparkle.controller.ui.dto.HomeStatisticsModels.ChartSeries;
import com.ghostchu.btn.sparkle.controller.ui.dto.HomeStatisticsModels.DistributionChart;
import com.ghostchu.btn.sparkle.controller.ui.dto.HomeStatisticsModels.HomeStatisticsResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.HomeStatisticsModels.StackedAreaChart;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.HomeStatisticsMapper;
import com.ghostchu.btn.sparkle.service.dto.DashboardDailyCountDto;
import com.ghostchu.btn.sparkle.service.dto.DashboardDimensionCountDto;
import com.ghostchu.btn.sparkle.service.dto.DashboardUserAgentCountDto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class HomeStatisticsService {
    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 7, 14, 30);
    private static final Set<String> ALLOWED_SOURCES = Set.of("ban", "all");
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_KEY_PREFIX = "sparkle:home:statistics:v2:";
    private static final Pattern CLIENT_VERSION_PATTERN = Pattern.compile(
            "([a-z][a-z0-9._ +-]{0,63})/([0-9]+(?:\\.[0-9a-z.-]+){0,3})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PEER_BAN_HELPER_VERSION_PATTERN = Pattern.compile(
            "PeerBanHelper/([0-9]+(?:\\.[0-9a-z.-]+){0,3})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> IGNORED_USER_AGENT_PRODUCTS = Set.of(
            "mozilla", "applewebkit", "chrome", "safari", "gecko",
            "trident", "edg", "firefox", "windows", "macintosh", "linux", "android"
    );
    private static final int TOP_DIMENSIONS = 10;
    private static final int TOP_CITIES = 8;

    private final HomeStatisticsMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Object> cacheLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();
    private final AtomicBoolean warming = new AtomicBoolean();

    public HomeStatisticsService(
            HomeStatisticsMapper mapper,
            JdbcTemplate jdbcTemplate,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public @NotNull HomeStatisticsResponse get(int days, String requestedSource)
            throws BusinessException {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported statistics range");
        }
        String source = requestedSource == null
                ? "ban"
                : requestedSource.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SOURCES.contains(source)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported statistics source");
        }

        String cacheKey = CACHE_KEY_PREFIX + days + ":" + source;
        HomeStatisticsResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        Object cacheLock = cacheLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (cacheLock) {
            cached = readCache(cacheKey);
            if (cached != null) {
                return cached;
            }
            HomeStatisticsResponse response = calculate(days, source);
            writeCache(cacheKey, response);
            return response;
        }
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 20_000)
    public void warmDefaultCache() {
        if (!warming.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                get(7, "ban");
            } catch (Exception exception) {
                log.warn("Unable to warm home statistics cache", exception);
            } finally {
                warming.set(false);
            }
        });
    }

    private HomeStatisticsResponse calculate(int days, String source) {
        TimeRange range = range(days);
        int bucketMinutes = days == 1 ? 60 : 1_440;
        boolean useAggregates = "ban".equals(source)
                && days > 1
                && dashboardAggregatesAvailable();
        try {
            return calculateWith(days, source, range, bucketMinutes, useAggregates);
        } catch (Exception exception) {
            if (!useAggregates) {
                throw exception;
            }
            log.warn("Home statistics aggregate query failed, falling back to raw tables",
                    exception);
            return calculateWith(days, source, range, bucketMinutes, false);
        }
    }

    private HomeStatisticsResponse calculateWith(
            int days,
            String source,
            TimeRange range,
            int bucketMinutes,
            boolean useAggregates
    ) {
        long startedNanos = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<DistributionChart> peerIds = executor.submit(() -> distribution(timedQuery(
                    "peer IDs",
                    () -> mapper.selectTopPeerIds(
                            range.startAt(), range.endAt(), TOP_DIMENSIONS, source, useAggregates
                    )
            )));
            Future<DistributionChart> clientNames = executor.submit(() -> distribution(timedQuery(
                    "client names",
                    () -> mapper.selectTopClientNames(
                            range.startAt(), range.endAt(), TOP_DIMENSIONS, source, useAggregates
                    )
            )));
            Future<StackedAreaChart> geoCities = executor.submit(() -> stackedChart(
                    timedQuery("GeoIP cities", () -> mapper.selectGeoCityTrend(
                            range.startAt(),
                            range.endAt(),
                            bucketMinutes,
                            source,
                            useAggregates
                    )),
                    range,
                    bucketMinutes,
                    TOP_CITIES,
                    List.of()
            ));
            Future<StackedAreaChart> ipVersions = executor.submit(() -> stackedChart(
                    timedQuery("IP versions", () -> mapper.selectIpVersionTrend(
                            range.startAt(),
                            range.endAt(),
                            bucketMinutes,
                            source,
                            useAggregates
                    )),
                    range,
                    bucketMinutes,
                    0,
                    List.of("IPv4", "IPv6", "Unknown")
            ));
            Future<DistributionChart> companionVersions = executor.submit(() ->
                    timedQuery("companion versions", this::companionVersions));
            Future<DistributionChart> companionIps = executor.submit(() ->
                    timedQuery("companion IP versions", this::companionIpVersions));
            Future<DistributionChart> companionGeoIp = executor.submit(() ->
                    timedQuery("companion GeoIP", () ->
                            distribution(mapper.selectCompanionGeoIpDistribution())));

            HomeStatisticsResponse response = new HomeStatisticsResponse(
                    days,
                    source,
                    await(peerIds),
                    await(clientNames),
                    await(geoCities),
                    await(ipVersions),
                    await(companionVersions),
                    await(companionIps),
                    await(companionGeoIp),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            log.info("Loaded home statistics for {} days from {} in {}ms (aggregates={})",
                    days,
                    source,
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                    useAggregates);
            return response;
        }
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private <T> T timedQuery(String name, java.util.function.Supplier<T> query) {
        long startedNanos = System.nanoTime();
        try {
            return query.get();
        } finally {
            log.info("Home statistics query '{}' completed in {}ms",
                    name,
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        }
    }

    private DistributionChart companionVersions() {
        Map<String, Long> grouped = new HashMap<>();
        for (DashboardUserAgentCountDto item : mapper.selectActiveUserAgents()) {
            String label = clientVersion(item.getUserAgent());
            grouped.merge(label, item.getValue(), Long::sum);
        }
        return distributionFromMap(grouped, TOP_DIMENSIONS);
    }

    private DistributionChart companionIpVersions() {
        return distribution(mapper.selectCompanionIpVersions());
    }

    private DistributionChart distribution(List<DashboardDimensionCountDto> rows) {
        if (rows.isEmpty()) {
            return new DistributionChart(List.of());
        }
        List<ChartDatum> data = new ArrayList<>();
        long total = rows.getFirst().getTotal();
        long shown = 0;
        for (DashboardDimensionCountDto row : rows) {
            data.add(new ChartDatum(row.getName(), row.getValue()));
            shown += row.getValue();
        }
        if (total > shown) {
            data.add(new ChartDatum("Other", total - shown));
        }
        return new DistributionChart(data);
    }

    private DistributionChart distributionFromMap(Map<String, Long> grouped, int limit) {
        List<Map.Entry<String, Long>> entries = grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        List<ChartDatum> data = new ArrayList<>();
        long total = 0;
        long shown = 0;
        int count = 0;
        for (Map.Entry<String, Long> entry : entries) {
            total += entry.getValue();
            if (count < limit) {
                data.add(new ChartDatum(entry.getKey(), entry.getValue()));
                shown += entry.getValue();
                count++;
            }
        }
        if (total > shown) {
            data.add(new ChartDatum("Other", total - shown));
        }
        return new DistributionChart(data);
    }

    private StackedAreaChart stackedChart(
            List<DashboardDailyCountDto> rows,
            TimeRange range,
            int bucketMinutes,
            int topDimensions,
            List<String> alwaysInclude
    ) {
        List<OffsetDateTime> buckets = buckets(range.startAt(), range.endAt(), bucketMinutes);
        List<String> categories = buckets.stream().map(OffsetDateTime::toString).toList();
        if (rows.isEmpty()) {
            return new StackedAreaChart(categories, List.of());
        }

        Map<OffsetDateTime, Map<String, Long>> values = new LinkedHashMap<>();
        Map<String, Long> totals = new HashMap<>();
        for (DashboardDailyCountDto row : rows) {
            values.computeIfAbsent(row.getBucket(), ignored -> new HashMap<>())
                    .merge(row.getName(), row.getValue(), Long::sum);
            totals.merge(row.getName(), row.getValue(), Long::sum);
        }

        List<String> seriesNames = new ArrayList<>();
        if (topDimensions > 0) {
            seriesNames.addAll(totals.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(topDimensions)
                    .map(Map.Entry::getKey)
                    .toList());
        }
        for (String name : alwaysInclude) {
            if (!seriesNames.contains(name) && (totals.containsKey(name) || !"Unknown".equals(name))) {
                seriesNames.add(name);
            }
        }

        Set<String> included = new HashSet<>(seriesNames);
        boolean hasOther = totals.keySet().stream().anyMatch(name -> !included.contains(name));
        if (hasOther) {
            seriesNames.add("Other");
        }

        List<ChartSeries> series = new ArrayList<>();
        for (String name : seriesNames) {
            List<Long> points = new ArrayList<>(buckets.size());
            for (OffsetDateTime bucket : buckets) {
                Map<String, Long> bucketValues = values.getOrDefault(bucket, Map.of());
                long value;
                if ("Other".equals(name)) {
                    value = bucketValues.entrySet().stream()
                            .filter(entry -> !included.contains(entry.getKey()))
                            .mapToLong(Map.Entry::getValue)
                            .sum();
                } else {
                    value = bucketValues.getOrDefault(name, 0L);
                }
                points.add(value);
            }
            series.add(new ChartSeries(name, points));
        }
        return new StackedAreaChart(categories, series);
    }

    private List<OffsetDateTime> buckets(
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            int bucketMinutes
    ) {
        List<OffsetDateTime> buckets = new ArrayList<>();
        OffsetDateTime cursor = startAt;
        while (cursor.isBefore(endAt)) {
            buckets.add(cursor);
            cursor = cursor.plusMinutes(bucketMinutes);
        }
        return buckets;
    }

    private String clientVersion(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        Matcher peerBanHelper = PEER_BAN_HELPER_VERSION_PATTERN.matcher(userAgent);
        if (peerBanHelper.find()) {
            return "PeerBanHelper/" + peerBanHelper.group(1);
        }
        Matcher matcher = CLIENT_VERSION_PATTERN.matcher(userAgent);
        while (matcher.find()) {
            String product = matcher.group(1).trim();
            if (IGNORED_USER_AGENT_PRODUCTS.contains(product.toLowerCase(Locale.ROOT))) {
                continue;
            }
            return product + "/" + matcher.group(2);
        }
        return "Unknown";
    }

    private boolean dashboardAggregatesAvailable() {
        try {
            Boolean available = jdbcTemplate.queryForObject("""
                    SELECT to_regclass('public.dashboard_ban_daily_peer') IS NOT NULL
                       AND to_regclass('public.dashboard_ban_daily_client') IS NOT NULL
                       AND to_regclass('public.dashboard_ban_daily_geo') IS NOT NULL
                    """, Boolean.class);
            boolean enabled = Boolean.TRUE.equals(available);
            if (!enabled) {
                log.warn("Home statistics aggregates are unavailable; using raw tables");
            }
            return enabled;
        } catch (Exception exception) {
            log.warn("Unable to detect home statistics aggregates; using raw tables", exception);
            return false;
        }
    }

    private TimeRange range(int days) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (days == 1) {
            OffsetDateTime endAt = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            return new TimeRange(endAt.minusHours(24), endAt);
        }
        OffsetDateTime endAt = now.toLocalDate().plusDays(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);
        return new TimeRange(endAt.minusDays(days), endAt);
    }

    private HomeStatisticsResponse readCache(String key) {
        LocalCacheEntry local = localCache.get(key);
        long now = System.currentTimeMillis();
        if (local != null && local.expiresAt() > now) {
            return local.response();
        }
        if (local != null) {
            localCache.remove(key, local);
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            HomeStatisticsResponse response = objectMapper.readValue(value, HomeStatisticsResponse.class);
            localCache.put(key, new LocalCacheEntry(response, now + CACHE_TTL.toMillis()));
            return response;
        } catch (Exception exception) {
            return null;
        }
    }

    private void writeCache(String key, HomeStatisticsResponse response) {
        localCache.put(key, new LocalCacheEntry(
                response,
                System.currentTimeMillis() + CACHE_TTL.toMillis()
        ));
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(response),
                    CACHE_TTL
            );
        } catch (Exception ignored) {
            // Statistics are still available when Redis is temporarily unavailable.
        }
    }

    private record TimeRange(OffsetDateTime startAt, OffsetDateTime endAt) {
    }

    private record LocalCacheEntry(HomeStatisticsResponse response, long expiresAt) {
    }
}
