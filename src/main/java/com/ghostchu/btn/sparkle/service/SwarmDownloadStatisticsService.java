package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.SwarmDownloadStatisticsModels.SwarmDownloadCidrEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.SwarmDownloadStatisticsModels.SwarmDownloadCidrResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.SwarmDownloadStatisticsMapper;
import com.ghostchu.btn.sparkle.service.dto.SwarmDownloadCidrRowDto;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class SwarmDownloadStatisticsService {
    private static final BigInteger BYTES_PER_TB = BigInteger.valueOf(1024).pow(4);
    private static final int RESULT_LIMIT = 200;
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final String CACHE_KEY_PREFIX = "sparkle:home:swarm-download-cidr:v1:";
    private static final Set<String> IP_VERSIONS = Set.of("all", "4", "6");

    private final SwarmDownloadStatisticsMapper mapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Object> cacheLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    public SwarmDownloadStatisticsService(
            SwarmDownloadStatisticsMapper mapper,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public @NotNull SwarmDownloadCidrResponse getStatistics(
            int hours,
            int ipv4Prefix,
            int ipv6Prefix,
            String ipVersion,
            BigDecimal thresholdTb
    ) throws BusinessException {
        Query query = validate(hours, ipv4Prefix, ipv6Prefix, ipVersion, thresholdTb);
        String key = cacheKey(query);
        SwarmDownloadCidrResponse cached = readCache(key);
        if (cached != null) {
            return cached;
        }

        Object cacheLock = cacheLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (cacheLock) {
            cached = readCache(key);
            if (cached != null) {
                return cached;
            }
            SwarmDownloadCidrResponse response = calculate(query);
            writeCache(key, response);
            return response;
        }
    }

    public void refresh() throws BusinessException {
        Query query = validate(24, 32, 56, "all", new BigDecimal("3"));
        String key = cacheKey(query);
        synchronized (cacheLocks.computeIfAbsent(key, ignored -> new Object())) {
            writeCache(key, calculate(query));
        }
    }

    private SwarmDownloadCidrResponse calculate(Query query) {
        OffsetDateTime windowEnd = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowStart = windowEnd.minusHours(query.hours());
        long startedNanos = System.nanoTime();
        List<SwarmDownloadCidrRowDto> rows = mapper.selectCidrDownloads(
                windowStart,
                windowEnd,
                query.ipv4Prefix(),
                query.ipv6Prefix(),
                query.ipVersionFilter(),
                query.thresholdBytes(),
                RESULT_LIMIT
        );

        List<SwarmDownloadCidrEntry> entries = rows.stream()
                .map(row -> new SwarmDownloadCidrEntry(
                        row.getCidr(),
                        row.getIpVersion(),
                        row.getDownloadBytes(),
                        row.getReporterCount(),
                        row.getConnectionCount(),
                        row.getTorrentCount(),
                        row.getLatestSeenAt()
                ))
                .toList();
        long matchingGroups = rows.isEmpty() ? 0 : rows.getFirst().getMatchingGroups();
        SwarmDownloadCidrResponse response = new SwarmDownloadCidrResponse(
                query.hours(),
                query.ipv4Prefix(),
                query.ipv6Prefix(),
                query.ipVersion(),
                query.thresholdTb().stripTrailingZeros().toPlainString(),
                query.thresholdBytes().toString(),
                entries,
                matchingGroups,
                matchingGroups > entries.size(),
                windowStart,
                windowEnd,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        log.info(
                "Loaded swarm download CIDR statistics for {} hours, IPv4 /{} and IPv6 /{} "
                        + "in {}ms ({} groups)",
                query.hours(),
                query.ipv4Prefix(),
                query.ipv6Prefix(),
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                matchingGroups
        );
        return response;
    }

    private Query validate(
            int hours,
            int ipv4Prefix,
            int ipv6Prefix,
            String ipVersion,
            BigDecimal thresholdTb
    ) throws BusinessException {
        if (hours < 1 || hours > 168) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Statistics window must be between 1 and 168 hours"
            );
        }
        if (ipv4Prefix < 8 || ipv4Prefix > 32) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "IPv4 CIDR prefix must be between 8 and 32"
            );
        }
        if (ipv6Prefix < 16 || ipv6Prefix > 128) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "IPv6 CIDR prefix must be between 16 and 128"
            );
        }

        String normalizedVersion = ipVersion == null
                ? "all"
                : ipVersion.trim().toLowerCase(Locale.ROOT);
        if (!IP_VERSIONS.contains(normalizedVersion)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "IP version must be all, 4, or 6"
            );
        }
        if (thresholdTb == null
                || thresholdTb.signum() <= 0
                || thresholdTb.compareTo(new BigDecimal("1000000")) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Download threshold must be greater than 0 and at most 1,000,000 TB"
            );
        }

        BigInteger thresholdBytes = thresholdTb
                .multiply(new BigDecimal(BYTES_PER_TB))
                .setScale(0, RoundingMode.CEILING)
                .toBigIntegerExact();
        Integer versionFilter = "all".equals(normalizedVersion)
                ? null
                : Integer.valueOf(normalizedVersion);
        return new Query(
                hours,
                ipv4Prefix,
                ipv6Prefix,
                normalizedVersion,
                versionFilter,
                thresholdTb.stripTrailingZeros(),
                thresholdBytes
        );
    }

    private String cacheKey(Query query) {
        return CACHE_KEY_PREFIX
                + query.hours() + ":"
                + query.ipv4Prefix() + ":"
                + query.ipv6Prefix() + ":"
                + query.ipVersion() + ":"
                + query.thresholdBytes();
    }

    private SwarmDownloadCidrResponse readCache(String key) {
        LocalCacheEntry local = localCache.get(key);
        long now = System.currentTimeMillis();
        if (local != null && local.expiresAt() > now) {
            return (SwarmDownloadCidrResponse) local.response();
        }
        if (local != null) {
            localCache.remove(key, local);
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            SwarmDownloadCidrResponse response = objectMapper.readValue(
                    value,
                    SwarmDownloadCidrResponse.class
            );
            localCache.put(key, new LocalCacheEntry(response, now + CACHE_TTL.toMillis()));
            return response;
        } catch (Exception exception) {
            return null;
        }
    }

    private void writeCache(String key, SwarmDownloadCidrResponse response) {
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
        } catch (Exception exception) {
            log.debug("Unable to cache swarm download CIDR statistics: {}",
                    exception.getMessage());
        }
    }

    private record Query(
            int hours,
            int ipv4Prefix,
            int ipv6Prefix,
            String ipVersion,
            Integer ipVersionFilter,
            BigDecimal thresholdTb,
            BigInteger thresholdBytes
    ) {
    }

    private record LocalCacheEntry(Object response, long expiresAt) {
    }
}
