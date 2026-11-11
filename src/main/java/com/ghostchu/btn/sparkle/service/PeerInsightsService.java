package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.BanPeerInsightsResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.PeerTorrentSeries;
import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.PeerVersionCount;
import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.SwarmPeerInsightsResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeerInsightsService {
    public static final String SWARM_MODULE_ID = "swarm-peer-distribution";
    public static final String BAN_MODULE_ID = "ban-peer-distribution";

    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 3, 7);
    private static final int TOP_TORRENTS = 50;

    private final JdbcTemplate jdbcTemplate;
    private final AnalyseModuleConfigService moduleConfigService;

    @Transactional
    public void refreshSwarm() {
        if (!moduleConfigService.isEnabled(SWARM_MODULE_ID)) {
            return;
        }
        long startedNanos = System.nanoTime();
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<HotTorrent> hotTorrents = jdbcTemplate.query(
                """
                SELECT
                    torrent_id,
                    COUNT(DISTINCT peer_ip) AS peer_count,
                    COUNT(DISTINCT peer_ip) FILTER (WHERE family(peer_ip) = 4)
                        AS ipv4_peer_count,
                    COUNT(DISTINCT peer_ip) FILTER (WHERE family(peer_ip) = 6)
                        AS ipv6_peer_count
                FROM public.swarm_tracker
                WHERE last_time_seen >= NOW() - INTERVAL '1 hour'
                  AND last_time_seen <= NOW()
                GROUP BY torrent_id
                ORDER BY peer_count DESC, torrent_id ASC
                LIMIT 50
                """,
                (resultSet, rowNum) -> new HotTorrent(
                        resultSet.getLong("torrent_id"),
                        resultSet.getLong("peer_count"),
                        resultSet.getLong("ipv4_peer_count"),
                        resultSet.getLong("ipv6_peer_count"),
                        rowNum + 1
                )
        );
        long ipv4Peers = hotTorrents.stream()
                .mapToLong(HotTorrent::ipv4PeerCount)
                .sum();
        long ipv6Peers = hotTorrents.stream()
                .mapToLong(HotTorrent::ipv6PeerCount)
                .sum();
        insertPeerVersions(
                "SWARM",
                capturedAt,
                List.of(new PeerVersionSnapshot(1, ipv4Peers, ipv6Peers))
        );
        insertHotTorrents(capturedAt, hotTorrents);
        log.info("Refreshed swarm peer insights in {}ms ({} hot torrents)",
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                hotTorrents.size());
    }

    @Transactional
    public void refreshBan() {
        if (!moduleConfigService.isEnabled(BAN_MODULE_ID)) {
            return;
        }
        long startedNanos = System.nanoTime();
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<PeerVersionSnapshot> versions = jdbcTemplate.query(
                """
                WITH peers AS (
                    SELECT
                        peer_ip,
                        family(peer_ip) AS ip_version,
                        BOOL_OR(insert_time >= NOW() - INTERVAL '24 hours') AS within_24h,
                        BOOL_OR(insert_time >= NOW() - INTERVAL '3 days') AS within_3d
                    FROM public.ban_history
                    WHERE insert_time >= NOW() - INTERVAL '7 days'
                    GROUP BY peer_ip
                )
                SELECT
                    ranges.range_hours,
                    COUNT(*) FILTER (WHERE peers.ip_version = 4) AS ipv4_peers,
                    COUNT(*) FILTER (WHERE peers.ip_version = 6) AS ipv6_peers
                FROM peers
                CROSS JOIN (VALUES (24), (72), (168)) AS ranges(range_hours)
                WHERE
                    (ranges.range_hours = 24 AND peers.within_24h)
                    OR (ranges.range_hours = 72 AND peers.within_3d)
                    OR ranges.range_hours = 168
                GROUP BY ranges.range_hours
                ORDER BY ranges.range_hours
                """,
                (resultSet, rowNum) -> new PeerVersionSnapshot(
                        resultSet.getInt("range_hours"),
                        resultSet.getLong("ipv4_peers"),
                        resultSet.getLong("ipv6_peers")
                )
        );
        insertPeerVersions("BAN", capturedAt, versions);
        log.info("Refreshed ban peer insights in {}ms",
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    public SwarmPeerInsightsResponse swarmInsights(int requestedDays)
            throws BusinessException {
        int days = validateDays(requestedDays);
        boolean enabled = moduleConfigService.isEnabled(SWARM_MODULE_ID);
        if (!enabled) {
            return new SwarmPeerInsightsResponse(
                    false,
                    days,
                    List.of(),
                    List.of(),
                    List.of(),
                    OffsetDateTime.now()
            );
        }

        List<PeerVersionCount> versions = loadLatestPeerVersions("SWARM", 1);
        int bucketHours = days == 1 ? 1 : days == 3 ? 3 : 6;
        List<TorrentPoint> points = jdbcTemplate.query(
                """
                WITH top_torrents AS (
                    SELECT torrent_id, MAX(peer_count) AS max_peers
                    FROM public.swarm_hot_torrent_snapshot
                    WHERE captured_at >= NOW() - (?::integer * INTERVAL '1 day')
                    GROUP BY torrent_id
                    ORDER BY max_peers DESC, torrent_id ASC
                    LIMIT 50
                ),
                bucketed AS (
                    SELECT
                        time_bucket((?::integer * INTERVAL '1 hour'), s.captured_at) AS bucket,
                        s.torrent_id,
                        MAX(s.peer_count) AS peer_count
                    FROM public.swarm_hot_torrent_snapshot s
                    INNER JOIN top_torrents t ON t.torrent_id = s.torrent_id
                    WHERE s.captured_at >= NOW() - (?::integer * INTERVAL '1 day')
                    GROUP BY bucket, s.torrent_id
                )
                SELECT bucket, torrent_id, peer_count
                FROM bucketed
                ORDER BY bucket, torrent_id
                """,
                (resultSet, rowNum) -> new TorrentPoint(
                        resultSet.getObject("bucket", OffsetDateTime.class),
                        resultSet.getLong("torrent_id"),
                        resultSet.getLong("peer_count")
                ),
                days,
                bucketHours,
                days
        );
        return buildSwarmResponse(days, versions, points);
    }

    public BanPeerInsightsResponse banInsights(int requestedDays)
            throws BusinessException {
        int days = validateDays(requestedDays);
        boolean enabled = moduleConfigService.isEnabled(BAN_MODULE_ID);
        return new BanPeerInsightsResponse(
                enabled,
                days,
                enabled ? loadLatestPeerVersions("BAN", days * 24) : List.of(),
                OffsetDateTime.now()
        );
    }

    private void insertPeerVersions(
            String dataset,
            OffsetDateTime capturedAt,
            List<PeerVersionSnapshot> versions
    ) {
        if (versions.isEmpty()) {
            return;
        }
        List<Object[]> rows = versions.stream()
                .map(version -> new Object[]{
                        capturedAt,
                        dataset,
                        version.rangeHours(),
                        version.ipv4Peers(),
                        version.ipv6Peers()
                })
                .toList();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO public.peer_version_snapshot (
                    captured_at, dataset, range_hours, ipv4_peers, ipv6_peers
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                rows
        );
    }

    private void insertHotTorrents(
            OffsetDateTime capturedAt,
            List<HotTorrent> torrents
    ) {
        if (torrents.isEmpty()) {
            return;
        }
        List<Object[]> rows = torrents.stream()
                .map(torrent -> new Object[]{
                        capturedAt,
                        torrent.torrentId(),
                        torrent.peerCount(),
                        torrent.ipv4PeerCount(),
                        torrent.ipv6PeerCount(),
                        torrent.rank()
                })
                .toList();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO public.swarm_hot_torrent_snapshot (
                    captured_at, torrent_id, peer_count,
                    ipv4_peer_count, ipv6_peer_count, rank
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                rows
        );
    }

    private List<PeerVersionCount> loadLatestPeerVersions(String dataset, int rangeHours) {
        return jdbcTemplate.query(
                """
                SELECT ipv4_peers, ipv6_peers
                FROM public.peer_version_snapshot
                WHERE dataset = ? AND range_hours = ?
                ORDER BY captured_at DESC
                LIMIT 1
                """,
                (resultSet, rowNum) -> List.of(
                        new PeerVersionCount("IPv4", resultSet.getLong("ipv4_peers")),
                        new PeerVersionCount("IPv6", resultSet.getLong("ipv6_peers"))
                ),
                dataset,
                rangeHours
        ).stream().findFirst().orElse(List.of());
    }

    private SwarmPeerInsightsResponse buildSwarmResponse(
            int days,
            List<PeerVersionCount> versions,
            List<TorrentPoint> points
    ) {
        Map<Long, Map<OffsetDateTime, Long>> byTorrent = new LinkedHashMap<>();
        TreeSet<OffsetDateTime> categories = new TreeSet<>();
        for (TorrentPoint point : points) {
            categories.add(point.bucket());
            byTorrent.computeIfAbsent(point.torrentId(), ignored -> new LinkedHashMap<>())
                    .put(point.bucket(), point.peerCount());
        }
        List<OffsetDateTime> orderedCategories = new ArrayList<>(categories);
        Map<Long, Long> torrentMax = new LinkedHashMap<>();
        byTorrent.forEach((torrentId, values) -> torrentMax.put(
                torrentId,
                values.values().stream().mapToLong(Long::longValue).max().orElse(0L)
        ));
        List<PeerTorrentSeries> series = byTorrent.entrySet().stream()
                .sorted((first, second) -> {
                    int compare = Long.compare(
                            torrentMax.getOrDefault(second.getKey(), 0L),
                            torrentMax.getOrDefault(first.getKey(), 0L)
                    );
                    return compare != 0
                            ? compare
                            : Long.compare(first.getKey(), second.getKey());
                })
                .limit(TOP_TORRENTS)
                .map(entry -> new PeerTorrentSeries(
                        Long.toString(entry.getKey()),
                        orderedCategories.stream()
                                .map(bucket -> entry.getValue().getOrDefault(bucket, 0L))
                                .toList()
                ))
                .toList();
        return new SwarmPeerInsightsResponse(
                true,
                days,
                versions,
                orderedCategories.stream().map(OffsetDateTime::toString).toList(),
                series,
                OffsetDateTime.now()
        );
    }

    private int validateDays(int days) throws BusinessException {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Peer insight range must be 1, 3, or 7 days"
            );
        }
        return days;
    }

    private record PeerVersionSnapshot(
            int rangeHours,
            long ipv4Peers,
            long ipv6Peers
    ) {
    }

    private record HotTorrent(
            long torrentId,
            long peerCount,
            long ipv4PeerCount,
            long ipv6PeerCount,
            int rank
    ) {
    }

    private record TorrentPoint(
            OffsetDateTime bucket,
            long torrentId,
            long peerCount
    ) {
    }
}
