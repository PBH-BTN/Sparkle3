package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import com.ghostchu.btn.sparkle.mapper.PointsDynamicMapper;
import com.ghostchu.btn.sparkle.util.JdbcTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsDynamicService {
    public static final String MODULE_ID = "points-dynamic";
    private static final String JOB_ID = "points-dynamic-hourly";
    private static final int MAX_BACKFILL_HOURS = 1;

    private final PointsDynamicMapper pointsDynamicMapper;
    private final PointsService pointsService;
    private final AnalyseModuleConfigService moduleConfigService;
    private final PointToggleService pointToggleService;
    private final PointRulesService pointRulesService;

    public void refresh() {
        if (!moduleConfigService.isEnabled(MODULE_ID)) {
            return;
        }
        long startedNanos = System.nanoTime();
        OffsetDateTime endAt = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS);
        if (!pointToggleService.isAnyDynamicEnabled()) {
            updateLastSuccess(endAt.minusHours(1));
            return;
        }
        OffsetDateTime startAt = loadLastSuccess()
                .orElseGet(() -> endAt.minusHours(1));
        if (!startAt.isBefore(endAt)) {
            return;
        }
        OffsetDateTime earliestAllowed = endAt.minusHours(MAX_BACKFILL_HOURS);
        if (startAt.isBefore(earliestAllowed)) {
            log.warn(
                    "Limiting dynamic points backfill from {} to {}",
                    startAt,
                    earliestAllowed
            );
            startAt = earliestAllowed;
        }

        int settledEvents = 0;
        OffsetDateTime cursor = startAt;
        while (cursor.isBefore(endAt)) {
            OffsetDateTime chunkEnd = cursor.plusHours(1);
            if (chunkEnd.isAfter(endAt)) {
                chunkEnd = endAt;
            }
            settledEvents += settleWindow(cursor, chunkEnd);
            updateLastSuccess(chunkEnd);
            cursor = chunkEnd;
        }
        log.info(
                "Settled {} dynamic point events from {} to {} in {}ms",
                settledEvents,
                startAt,
                endAt,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()
        );
    }

    private int settleWindow(OffsetDateTime startAt, OffsetDateTime endAt) {
        Map<EventKey, DynamicEvent> events = new LinkedHashMap<>();
        loadTrafficEvents(startAt, endAt, events);
        loadHotPeerEvents(startAt, endAt, events);
        int settledEvents = 0;
        for (DynamicEvent event : events.values()) {
            PointAwardBucket bucket = new PointAwardBucket(
                    event.userId,
                    "DYNAMIC_POINT_SETTLEMENT",
                    event.bucket.plusHours(1)
            );
            event.categoryPoints.forEach((category, points) ->
                    bucket.addClaim(category.name(), event.bucket.toString(), category, points));
            pointsService.commit(bucket);
            settledEvents++;
        }
        return settledEvents;
    }

    private void loadTrafficEvents(
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            Map<EventKey, DynamicEvent> events
    ) {
        boolean uploadEnabled = pointToggleService.isEnabled(
                PointCategory.DYNAMIC_SEED_UPLOAD
        );
        boolean downloadEnabled = pointToggleService.isEnabled(
                PointCategory.DYNAMIC_DOWNLOAD
        );
        if (!uploadEnabled && !downloadEnabled) {
            return;
        }
        long torrentSizeGib = pointRulesService.dynamicTorrentSizeGib();
        pointsDynamicMapper.selectTrafficEvents(
                startAt,
                endAt,
                pointRulesService.points(PointCategory.DYNAMIC_SEED_UPLOAD),
                pointRulesService.dynamicSeedUploadMib(),
                pointRulesService.points(PointCategory.DYNAMIC_DOWNLOAD),
                pointRulesService.dynamicDownloadMib(),
                torrentSizeGib
        ).forEach(row -> {
            EventKey key = new EventKey(
                    JdbcTime.toOffsetDateTime(row.get("bucket")),
                    ((Number) row.get("user_id")).longValue()
            );
            DynamicEvent event = events.computeIfAbsent(
                    key,
                    ignored -> new DynamicEvent(key.bucket(), key.userId())
            );
            long uploadPoints = ((Number) row.get("upload_points")).longValue();
            long downloadPoints = ((Number) row.get("download_points")).longValue();
            if (uploadEnabled && uploadPoints > 0) {
                event.add(PointCategory.DYNAMIC_SEED_UPLOAD, uploadPoints);
            }
            if (downloadEnabled && downloadPoints > 0) {
                event.add(PointCategory.DYNAMIC_DOWNLOAD, downloadPoints);
            }
        });
    }

    private void loadHotPeerEvents(
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            Map<EventKey, DynamicEvent> events
    ) {
        if (!pointToggleService.isEnabled(PointCategory.DYNAMIC_HOT_PEER)) {
            return;
        }
        int hotPeerTop = pointRulesService.dynamicHotPeerTop();
        long hotPeerPoints = pointRulesService.points(PointCategory.DYNAMIC_HOT_PEER);
        pointsDynamicMapper.selectHotPeerEvents(
                startAt,
                endAt,
                hotPeerTop,
                hotPeerPoints
        ).forEach(row -> {
            EventKey key = new EventKey(
                    JdbcTime.toOffsetDateTime(row.get("bucket")),
                    ((Number) row.get("user_id")).longValue()
            );
            DynamicEvent event = events.computeIfAbsent(
                    key,
                    ignored -> new DynamicEvent(key.bucket(), key.userId())
            );
            event.add(
                    PointCategory.DYNAMIC_HOT_PEER,
                    ((Number) row.get("points")).longValue()
            );
        });
    }

    private Optional<OffsetDateTime> loadLastSuccess() {
        return Optional.ofNullable(pointsDynamicMapper.selectLastSuccess(JOB_ID));
    }

    private void updateLastSuccess(OffsetDateTime endAt) {
        pointsDynamicMapper.updateLastSuccess(JOB_ID, endAt);
    }

    public void resetCursor() {
        OffsetDateTime previousCompletedHour = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(1);
        updateLastSuccess(previousCompletedHour);
    }

    private record EventKey(OffsetDateTime bucket, long userId) {
    }

    private static final class DynamicEvent {
        private final OffsetDateTime bucket;
        private final long userId;
        private final Map<PointCategory, Long> categoryPoints = new LinkedHashMap<>();

        private DynamicEvent(OffsetDateTime bucket, long userId) {
            this.bucket = bucket;
            this.userId = userId;
        }

        private void add(PointCategory category, long points) {
            if (points > 0) {
                categoryPoints.merge(category, points, Long::sum);
            }
        }
    }
}
