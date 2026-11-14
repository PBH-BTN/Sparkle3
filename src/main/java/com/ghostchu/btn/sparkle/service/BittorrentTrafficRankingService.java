package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.TrafficRanking;
import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.TrafficRankingsResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class BittorrentTrafficRankingService {
    private static final String CACHE_KEY = "sparkle:home:bittorrent-traffic-rankings:v1";
    private final BittorrentTrafficMapper mapper;
    private final BittorrentTrafficFeatureService featureService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Object snapshotLock = new Object();
    private volatile Map<Integer, TrafficRankingsResponse> snapshot = Map.of();

    public BittorrentTrafficRankingService(BittorrentTrafficMapper mapper,
            BittorrentTrafficFeatureService featureService,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.featureService = featureService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public TrafficRankingsResponse getStatistics(int days) throws BusinessException {
        if (!Set.of(1, 3, 7).contains(days)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported statistics range");
        }
        if (!featureService.isEnabled()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Traffic statistics are disabled");
        }
        restoreSnapshot();
        return snapshot.getOrDefault(days, new TrafficRankingsResponse(days, List.of(), List.of(), null));
    }

    private void restoreSnapshot() {
        if (!snapshot.isEmpty()) return;
        synchronized (snapshotLock) {
            if (!snapshot.isEmpty()) return;
            try {
                String cached = redisTemplate.opsForValue().get(CACHE_KEY);
                if (cached == null) return;
                Map<Integer, TrafficRankingsResponse> restored = objectMapper.readValue(
                        cached, new TypeReference<Map<Integer, TrafficRankingsResponse>>() {});
                if (restored != null && restored.keySet().containsAll(Set.of(1, 3, 7))) {
                    snapshot = Map.copyOf(restored);
                }
            } catch (Exception exception) {
                // A missing or unavailable cache must never trigger traffic queries.
                log.debug("Unable to restore BitTorrent traffic rankings cache");
            }
        }
    }

    @Scheduled(fixedDelay = 43_200_000, initialDelay = 43_200_000)
    @MonitoredScheduledTask(id = "bittorrent-traffic-rankings", group = TaskGroup.STATISTICS,
            nameKey = "admin.tasks.items.bittorrent-traffic-rankings.name",
            descriptionKey = "admin.tasks.items.bittorrent-traffic-rankings.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = 43_200_000,
            initialDelayMillis = 43_200_000)
    public synchronized void refresh() {
        if (!featureService.isEnabled()) return;
        OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC);
        Map<Integer, TrafficRankingsResponse> next = new HashMap<>();
        for (int days : List.of(1, 3, 7)) {
            next.put(days, new TrafficRankingsResponse(days,
                    load(end.minusDays(days), end, true),
                    load(end.minusDays(days), end, false), end));
        }
        synchronized (snapshotLock) {
            snapshot = Map.copyOf(next);
        }
        try {
            // Store all windows together without expiry so restarts retain the last result.
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(snapshot));
        } catch (Exception exception) {
            log.warn("Unable to persist BitTorrent traffic rankings; keeping the in-memory snapshot");
        }
    }

    private List<TrafficRanking> load(OffsetDateTime start, OffsetDateTime end, boolean peerId) {
        return mapper.selectFeatureTraffic(start, end, peerId).stream()
                .map(row -> new TrafficRanking(row.getCity(), row.getUploadBytes(), row.getDownloadBytes()))
                .toList();
    }
}
