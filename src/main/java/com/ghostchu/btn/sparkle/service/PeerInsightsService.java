package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.BanPeerInsightsResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.PeerTorrentSeries;
import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.PeerVersionCount;
import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.SwarmPeerInsightsResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.PeerInsightsMapper;
import com.ghostchu.btn.sparkle.service.dto.HotTorrentRow;
import com.ghostchu.btn.sparkle.service.dto.PeerVersionSnapshotRow;
import com.ghostchu.btn.sparkle.service.dto.TorrentPointRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    private final PeerInsightsMapper peerInsightsMapper;
    private final AnalyseModuleConfigService moduleConfigService;

    @Transactional
    public void refreshSwarm() {
        if (!moduleConfigService.isEnabled(SWARM_MODULE_ID)) {
            return;
        }
        long startedNanos = System.nanoTime();
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<HotTorrentRow> hotTorrentRows = peerInsightsMapper.selectHotTorrents();
        List<HotTorrent> hotTorrents = java.util.stream.IntStream.range(
                0,
                hotTorrentRows.size()
        ).mapToObj(index -> {
            HotTorrentRow row = hotTorrentRows.get(index);
            return new HotTorrent(
                    row.getTorrentId(),
                    row.getPeerCount(),
                    row.getIpv4PeerCount(),
                    row.getIpv6PeerCount(),
                    index + 1
            );
        }).toList();
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
        List<PeerVersionSnapshot> versions = peerInsightsMapper
                .selectBanVersionSnapshots().stream()
                .map(row -> new PeerVersionSnapshot(
                        row.getRangeHours(),
                        row.getIpv4Peers(),
                        row.getIpv6Peers()
                ))
                .toList();
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
        List<TorrentPoint> points = peerInsightsMapper
                .selectTorrentPoints(days, bucketHours).stream()
                .map(row -> new TorrentPoint(
                        row.getBucket(),
                        row.getTorrentId(),
                        row.getPeerCount()
                ))
                .toList();
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
        versions.forEach(version -> peerInsightsMapper.insertPeerVersion(
                capturedAt,
                dataset,
                version.rangeHours(),
                version.ipv4Peers(),
                version.ipv6Peers()
        ));
    }

    private void insertHotTorrents(
            OffsetDateTime capturedAt,
            List<HotTorrent> torrents
    ) {
        if (torrents.isEmpty()) {
            return;
        }
        torrents.forEach(torrent -> peerInsightsMapper.insertHotTorrent(
                capturedAt,
                torrent.torrentId(),
                torrent.peerCount(),
                torrent.ipv4PeerCount(),
                torrent.ipv6PeerCount(),
                torrent.rank()
        ));
    }

    private List<PeerVersionCount> loadLatestPeerVersions(String dataset, int rangeHours) {
        PeerVersionSnapshotRow row = peerInsightsMapper.selectLatestPeerVersions(
                dataset,
                rangeHours
        );
        return row == null
                ? List.of()
                : List.of(
                        new PeerVersionCount("IPv4", row.getIpv4Peers()),
                        new PeerVersionCount("IPv6", row.getIpv6Peers())
                );
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
