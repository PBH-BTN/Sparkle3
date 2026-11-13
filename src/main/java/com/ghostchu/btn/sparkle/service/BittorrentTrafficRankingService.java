package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.TrafficRanking;
import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.TrafficRankingsResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class BittorrentTrafficRankingService {
    private final BittorrentTrafficMapper mapper;
    private final BittorrentTrafficFeatureService featureService;
    private volatile Map<Integer, TrafficRankingsResponse> snapshot = Map.of();

    public TrafficRankingsResponse getStatistics(int days) throws BusinessException {
        if (!Set.of(1, 3, 7).contains(days)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported statistics range");
        }
        if (!featureService.isEnabled()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Traffic statistics are disabled");
        }
        return snapshot.getOrDefault(days, new TrafficRankingsResponse(days, List.of(), List.of(), null));
    }

    @Scheduled(fixedDelay = 43_200_000, initialDelay = 30_000)
    @MonitoredScheduledTask(id = "bittorrent-traffic-rankings", group = TaskGroup.STATISTICS,
            nameKey = "admin.tasks.items.bittorrent-traffic-rankings.name",
            descriptionKey = "admin.tasks.items.bittorrent-traffic-rankings.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = 43_200_000,
            initialDelayMillis = 30_000)
    public synchronized void refresh() {
        if (!featureService.isEnabled()) return;
        OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC);
        Map<Integer, TrafficRankingsResponse> next = new HashMap<>();
        for (int days : List.of(1, 3, 7)) {
            next.put(days, new TrafficRankingsResponse(days,
                    load(end.minusDays(days), end, true),
                    load(end.minusDays(days), end, false), end));
        }
        snapshot = Map.copyOf(next);
    }

    private List<TrafficRanking> load(OffsetDateTime start, OffsetDateTime end, boolean peerId) {
        return mapper.selectFeatureTraffic(start, end, peerId).stream()
                .map(row -> new TrafficRanking(row.getCity(), row.getUploadBytes(), row.getDownloadBytes()))
                .toList();
    }
}
