package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbcTemplate;
    private final PointsService pointsService;
    private final AnalyseModuleConfigService moduleConfigService;
    private final PointToggleService pointToggleService;

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
        jdbcTemplate.query(
                """
                WITH traffic AS (
                    SELECT
                        time_bucket(INTERVAL '1 hour', occurred_at) AS bucket,
                        user_id,
                        torrent_id,
                        MAX(GREATEST(torrent_size, 0)) AS torrent_size,
                        SUM(GREATEST(upload_bytes, 0)) AS upload_bytes,
                        SUM(GREATEST(download_bytes, 0)) AS download_bytes
                    FROM public.swarm_traffic_delta
                    WHERE occurred_at >= ? AND occurred_at < ?
                    GROUP BY bucket, user_id, torrent_id
                ),
                scored AS (
                    SELECT
                        bucket,
                        user_id,
                        SUM(FLOOR(
                            180::numeric
                            * upload_bytes
                            * torrent_size
                            / (200::numeric * 1024 * 1024 * 1024 * 1024 * 1024)
                        ))::bigint AS upload_points,
                        SUM(FLOOR(
                            50::numeric
                            * download_bytes
                            * torrent_size
                            / (512::numeric * 1024 * 1024 * 1024 * 1024 * 1024)
                        ))::bigint AS download_points
                    FROM traffic
                    GROUP BY bucket, user_id
                )
                SELECT bucket, user_id, upload_points, download_points
                FROM scored
                WHERE upload_points > 0 OR download_points > 0
                """,
                resultSet -> {
                    while (resultSet.next()) {
                        EventKey key = new EventKey(
                                resultSet.getObject("bucket", OffsetDateTime.class),
                                resultSet.getLong("user_id")
                        );
                        DynamicEvent event = events.computeIfAbsent(
                                key,
                                ignored -> new DynamicEvent(key.bucket(), key.userId())
                        );
                        long uploadPoints = resultSet.getLong("upload_points");
                        long downloadPoints = resultSet.getLong("download_points");
                        if (uploadEnabled && uploadPoints > 0) {
                            event.add(PointCategory.DYNAMIC_SEED_UPLOAD, uploadPoints);
                        }
                        if (downloadEnabled && downloadPoints > 0) {
                            event.add(PointCategory.DYNAMIC_DOWNLOAD, downloadPoints);
                        }
                    }
                    return null;
                },
                startAt,
                endAt
        );
    }

    private void loadHotPeerEvents(
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            Map<EventKey, DynamicEvent> events
    ) {
        if (!pointToggleService.isEnabled(PointCategory.DYNAMIC_HOT_PEER)) {
            return;
        }
        jdbcTemplate.query(
                """
                WITH active AS (
                    SELECT
                        time_bucket(INTERVAL '1 hour', s.last_time_seen) AS bucket,
                        s.torrent_id,
                        s.userapps_id,
                        s.peer_ip,
                        s.peer_port,
                        s.peer_id,
                        u.owner AS user_id
                    FROM public.swarm_tracker s
                    INNER JOIN public.userapp u ON u.id = s.userapps_id
                    WHERE s.last_time_seen >= ? AND s.last_time_seen < ?
                ),
                torrent_counts AS (
                    SELECT
                        bucket,
                        torrent_id,
                        COUNT(DISTINCT peer_ip) AS unique_peers
                    FROM active
                    GROUP BY bucket, torrent_id
                ),
                top_torrents AS (
                    SELECT bucket, torrent_id, unique_peers
                    FROM (
                        SELECT
                            bucket,
                            torrent_id,
                            unique_peers,
                            ROW_NUMBER() OVER (
                                PARTITION BY bucket
                                ORDER BY unique_peers DESC, torrent_id ASC
                            ) AS rank
                        FROM torrent_counts
                    ) ranked
                    WHERE rank <= 50
                ),
                user_peers AS (
                    SELECT
                        bucket,
                        user_id,
                        torrent_id,
                        COUNT(DISTINCT COALESCE(
                            NULLIF(peer_id, ''),
                            host(peer_ip) || ':' || peer_port
                        )) AS peer_count
                    FROM active
                    GROUP BY bucket, user_id, torrent_id
                )
                SELECT
                    user_peers.bucket,
                    user_peers.user_id,
                    SUM(user_peers.peer_count * 100)::bigint AS points
                FROM user_peers
                INNER JOIN top_torrents
                    ON top_torrents.bucket = user_peers.bucket
                   AND top_torrents.torrent_id = user_peers.torrent_id
                GROUP BY user_peers.bucket, user_peers.user_id
                HAVING SUM(user_peers.peer_count * 100) > 0
                """,
                resultSet -> {
                    while (resultSet.next()) {
                        EventKey key = new EventKey(
                                resultSet.getObject("bucket", OffsetDateTime.class),
                                resultSet.getLong("user_id")
                        );
                        DynamicEvent event = events.computeIfAbsent(
                                key,
                                ignored -> new DynamicEvent(key.bucket(), key.userId())
                        );
                        event.add(
                                PointCategory.DYNAMIC_HOT_PEER,
                                resultSet.getLong("points")
                        );
                    }
                    return null;
                },
                startAt,
                endAt
        );
    }

    private Optional<OffsetDateTime> loadLastSuccess() {
        var values = jdbcTemplate.query(
                """
                SELECT last_success_at
                FROM public.point_job_state
                WHERE job_id = ?
                """,
                (resultSet, rowNum) -> resultSet.getObject(
                        "last_success_at",
                        OffsetDateTime.class
                ),
                JOB_ID
        );
        return values.isEmpty()
                ? Optional.empty()
                : Optional.of(values.getFirst());
    }

    private void updateLastSuccess(OffsetDateTime endAt) {
        jdbcTemplate.update(
                """
                INSERT INTO public.point_job_state (job_id, last_success_at, updated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (job_id) DO UPDATE
                SET last_success_at = EXCLUDED.last_success_at,
                    updated_at = NOW()
                """,
                JOB_ID,
                endAt
        );
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
