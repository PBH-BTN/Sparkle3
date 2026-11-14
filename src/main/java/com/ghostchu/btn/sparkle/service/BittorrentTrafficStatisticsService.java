package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.HomeBittorrentTrafficResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.TrafficCity;
import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.TrafficDistribution;
import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.TrafficTotals;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.service.dto.DashboardTrafficRowDto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@Slf4j
public class BittorrentTrafficStatisticsService {
    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 3, 7);
    private static final int TOP_CITIES = 30;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final long ACCESS_CACHE_MILLIS = 60_000L;
    private static final String CACHE_KEY_PREFIX = "sparkle:home:bittorrent-traffic:v3:";

    private final BittorrentTrafficMapper mapper;
    private final BittorrentTrafficSnapshotService snapshotService;
    private final BittorrentTrafficMapper trafficMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final BittorrentTrafficFeatureService featureService;
    private final ConcurrentMap<String, Object> cacheLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    private volatile Boolean banSnapshotAccessible;
    private volatile Boolean swarmSnapshotAccessible;
    private volatile long accessCheckedAt;

    public BittorrentTrafficStatisticsService(
            BittorrentTrafficMapper mapper,
            BittorrentTrafficSnapshotService snapshotService,
            BittorrentTrafficMapper trafficMapper,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            BittorrentTrafficFeatureService featureService
    ) {
        this.mapper = mapper;
        this.snapshotService = snapshotService;
        this.trafficMapper = trafficMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.featureService = featureService;
    }

    public @NotNull HomeBittorrentTrafficResponse getStatistics(int days)
            throws BusinessException {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported BitTorrent traffic statistics range"
            );
        }
        if (!featureService.isEnabled()) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "BitTorrent traffic statistics are disabled"
            );
        }

        String cacheKey = cacheKey(days);
        HomeBittorrentTrafficResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Requests must not start expensive queries during startup or cache outages.
        return new HomeBittorrentTrafficResponse(days,
                new TrafficTotals("0", "0", "0"),
                new TrafficDistribution(List.of(), "0"),
                new TrafficDistribution(List.of(), "0"), null);
    }

    public void refresh() throws BusinessException {
        if (!featureService.isEnabled()) {
            log.info("Skipping BitTorrent traffic statistics refresh because the module is disabled");
            return;
        }
        snapshotService.refresh();
        for (int days : List.of(7, 3, 1)) {
            String key = cacheKey(days);
            synchronized (cacheLocks.computeIfAbsent(key, ignored -> new Object())) {
                writeCache(key, calculate(days));
            }
        }
    }

    private HomeBittorrentTrafficResponse calculate(int days) {
        TimeRange range = range(days);
        long startedNanos = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<SourceTraffic> ban = executor.submit(() -> loadBanTraffic(range));
            Future<SourceTraffic> swarm = executor.submit(() -> loadSwarmTraffic(range));
            SourceTraffic banTraffic = await(ban);
            SourceTraffic swarmTraffic = await(swarm);

            BigInteger upload = banTraffic.upload().add(swarmTraffic.upload());
            BigInteger download = banTraffic.download().add(swarmTraffic.download());
            Map<String, TrafficAmounts> cities = combineCities(banTraffic, swarmTraffic);
            HomeBittorrentTrafficResponse response = new HomeBittorrentTrafficResponse(
                    days,
                    new TrafficTotals(
                            upload.toString(),
                            download.toString(),
                            upload.add(download).toString()
                    ),
                    distribution(cities, upload, true),
                    distribution(cities, download, false),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            log.info("Loaded BitTorrent traffic statistics for {} days in {}ms",
                    days,
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
            return response;
        }
    }

    private SourceTraffic loadBanTraffic(TimeRange range) {
        if (banSnapshotAccessible()) {
            try {
                return loadSource(mapper.selectBanTraffic(
                        range.startAt(),
                        range.endAt(),
                        range.days(),
                        TOP_CITIES,
                        true
                ));
            } catch (Exception exception) {
                markBanSnapshotUnavailable();
                log.warn("BitTorrent ban traffic snapshot query failed; using raw ban history",
                        exception);
            }
        }
        return loadSource(mapper.selectBanTraffic(
                range.startAt(),
                range.endAt(),
                range.days(),
                TOP_CITIES,
                false
        ));
    }

    private SourceTraffic loadSwarmTraffic(TimeRange range) {
        if (snapshotAccessible()) {
            try {
                return loadSource(mapper.selectSwarmTraffic(
                        range.startAt(),
                        range.endAt(),
                        TOP_CITIES,
                        true
                ));
            } catch (Exception exception) {
                markSnapshotUnavailable();
                log.warn("BitTorrent swarm traffic snapshot query failed; using raw swarm data",
                        exception);
            }
        }
        return loadSource(mapper.selectSwarmTraffic(
                range.startAt(),
                range.endAt(),
                TOP_CITIES,
                false
        ));
    }

    private SourceTraffic loadSource(List<DashboardTrafficRowDto> rows) {
        BigInteger upload = BigInteger.ZERO;
        BigInteger download = BigInteger.ZERO;
        Map<String, TrafficAmounts> cities = new HashMap<>();
        for (DashboardTrafficRowDto row : rows) {
            // MyBatis maps the all-NULL aggregate row for an empty window to null.
            if (row == null) {
                continue;
            }
            BigInteger rowUpload = parse(row.getUploadBytes());
            BigInteger rowDownload = parse(row.getDownloadBytes());
            if (row.getCity() == null) {
                upload = rowUpload;
                download = rowDownload;
                continue;
            }
            cities.merge(
                    row.getCity(),
                    new TrafficAmounts(rowUpload, rowDownload),
                    TrafficAmounts::plus
            );
        }
        return new SourceTraffic(upload, download, cities);
    }

    private Map<String, TrafficAmounts> combineCities(SourceTraffic... sources) {
        Map<String, TrafficAmounts> combined = new HashMap<>();
        for (SourceTraffic source : sources) {
            source.cities().forEach((city, amounts) ->
                    combined.merge(city, amounts, TrafficAmounts::plus));
        }
        return combined;
    }

    private TrafficDistribution distribution(
            Map<String, TrafficAmounts> cities,
            BigInteger total,
            boolean upload
    ) {
        List<Map.Entry<String, TrafficAmounts>> ranked = new ArrayList<>(cities.entrySet());
        ranked.removeIf(entry -> amount(entry.getValue(), upload).signum() <= 0);
        ranked.sort((first, second) -> {
            int valueComparison = amount(second.getValue(), upload)
                    .compareTo(amount(first.getValue(), upload));
            return valueComparison != 0
                    ? valueComparison
                    : first.getKey().compareTo(second.getKey());
        });

        List<TrafficCity> data = new ArrayList<>();
        BigInteger shown = BigInteger.ZERO;
        for (int index = 0; index < Math.min(TOP_CITIES, ranked.size()); index++) {
            Map.Entry<String, TrafficAmounts> entry = ranked.get(index);
            BigInteger value = amount(entry.getValue(), upload);
            shown = shown.add(value);
            data.add(new TrafficCity(entry.getKey(), value.toString()));
        }
        BigInteger other = total.subtract(shown);
        if (other.signum() > 0) {
            data.add(new TrafficCity("Other", other.toString()));
        }
        return new TrafficDistribution(List.copyOf(data), total.toString());
    }

    private BigInteger amount(TrafficAmounts amounts, boolean upload) {
        return upload ? amounts.upload() : amounts.download();
    }

    private BigInteger parse(String value) {
        if (value == null || value.isBlank()) {
            return BigInteger.ZERO;
        }
        try {
            return new BigInteger(value);
        } catch (NumberFormatException exception) {
            return BigInteger.ZERO;
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

    private boolean banSnapshotAccessible() {
        refreshAccessibility();
        return Boolean.TRUE.equals(banSnapshotAccessible);
    }

    private boolean snapshotAccessible() {
        refreshAccessibility();
        return Boolean.TRUE.equals(swarmSnapshotAccessible);
    }

    private void refreshAccessibility() {
        long now = System.currentTimeMillis();
        if (banSnapshotAccessible != null
                && swarmSnapshotAccessible != null
                && now < accessCheckedAt + ACCESS_CACHE_MILLIS) {
            return;
        }
        synchronized (this) {
            if (banSnapshotAccessible != null
                    && swarmSnapshotAccessible != null
                    && now < accessCheckedAt + ACCESS_CACHE_MILLIS) {
                return;
            }
            banSnapshotAccessible = relationReadable("dashboard_ban_city_traffic");
            swarmSnapshotAccessible = relationReadable("dashboard_swarm_hourly_traffic");
            accessCheckedAt = now;
        }
    }

    private boolean relationReadable(String relationName) {
        try {
            Boolean readable = trafficMapper.relationReadable(
                    "public." + relationName
            );
            return Boolean.TRUE.equals(readable);
        } catch (Exception exception) {
            log.warn("Unable to inspect BitTorrent traffic aggregate {}: {}",
                    relationName,
                    exception.getMessage());
            return false;
        }
    }

    private void markBanSnapshotUnavailable() {
        banSnapshotAccessible = false;
        accessCheckedAt = 0;
    }

    private void markSnapshotUnavailable() {
        swarmSnapshotAccessible = false;
        accessCheckedAt = 0;
    }

    private TimeRange range(int days) {
        OffsetDateTime endAt = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1);
        return new TimeRange(
                days,
                days == 1 ? endAt.minusHours(24) : endAt.minusDays(days),
                endAt
        );
    }

    private String cacheKey(int days) {
        return CACHE_KEY_PREFIX + days;
    }

    private HomeBittorrentTrafficResponse readCache(String key) {
        LocalCacheEntry local = localCache.get(key);
        long now = System.currentTimeMillis();
        if (local != null && local.expiresAt() > now) {
            return (HomeBittorrentTrafficResponse) local.response();
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return local == null ? null : (HomeBittorrentTrafficResponse) local.response();
            }
            HomeBittorrentTrafficResponse response = objectMapper.readValue(
                    value,
                    HomeBittorrentTrafficResponse.class
            );
            localCache.put(key, new LocalCacheEntry(response, now + CACHE_TTL.toMillis()));
            return response;
        } catch (Exception exception) {
            return local == null ? null : (HomeBittorrentTrafficResponse) local.response();
        }
    }

    private void writeCache(String key, HomeBittorrentTrafficResponse response) {
        localCache.put(key, new LocalCacheEntry(
                response,
                System.currentTimeMillis() + CACHE_TTL.toMillis()
        ));
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(response)
            );
        } catch (Exception ignored) {
            // Statistics remain available when Redis is temporarily unavailable.
        }
    }

    private record TimeRange(int days, OffsetDateTime startAt, OffsetDateTime endAt) {
    }

    private record TrafficAmounts(BigInteger upload, BigInteger download) {
        private TrafficAmounts plus(TrafficAmounts other) {
            return new TrafficAmounts(
                    upload.add(other.upload),
                    download.add(other.download)
            );
        }
    }

    private record SourceTraffic(
            BigInteger upload,
            BigInteger download,
            Map<String, TrafficAmounts> cities
    ) {
    }

    private record LocalCacheEntry(Object response, long expiresAt) {
    }
}
