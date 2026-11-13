package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.HotTorrentRow;
import com.ghostchu.btn.sparkle.service.dto.PeerVersionSnapshotRow;
import com.ghostchu.btn.sparkle.service.dto.TorrentPointRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

public interface PeerInsightsMapper {
    @Select("""
            SELECT torrent_id, COUNT(DISTINCT peer_ip) AS peer_count,
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
            """)
    List<HotTorrentRow> selectHotTorrents();

    @Select("""
            WITH peers AS (
                SELECT peer_ip, family(peer_ip) AS ip_version,
                       BOOL_OR(insert_time >= NOW() - INTERVAL '24 hours') AS within_24h,
                       BOOL_OR(insert_time >= NOW() - INTERVAL '3 days') AS within_3d
                FROM public.ban_history
                WHERE insert_time >= NOW() - INTERVAL '7 days'
                GROUP BY peer_ip
            )
            SELECT ranges.range_hours,
                   COUNT(*) FILTER (WHERE peers.ip_version = 4) AS ipv4_peers,
                   COUNT(*) FILTER (WHERE peers.ip_version = 6) AS ipv6_peers
            FROM peers
            CROSS JOIN (VALUES (24), (72), (168)) AS ranges(range_hours)
            WHERE (ranges.range_hours = 24 AND peers.within_24h)
               OR (ranges.range_hours = 72 AND peers.within_3d)
               OR ranges.range_hours = 168
            GROUP BY ranges.range_hours
            ORDER BY ranges.range_hours
            """)
    List<PeerVersionSnapshotRow> selectBanVersionSnapshots();

    @Select("""
            WITH top_torrents AS (
                SELECT torrent_id, MAX(peer_count) AS max_peers
                FROM public.swarm_hot_torrent_snapshot
                WHERE captured_at >= NOW() - (#{days}::integer * INTERVAL '1 day')
                GROUP BY torrent_id
                ORDER BY max_peers DESC, torrent_id ASC
                LIMIT 50
            ),
            bucketed AS (
                SELECT time_bucket(
                           (#{bucketHours}::integer * INTERVAL '1 hour'),
                           s.captured_at
                       ) AS bucket,
                       s.torrent_id,
                       MAX(s.peer_count) AS peer_count
                FROM public.swarm_hot_torrent_snapshot s
                INNER JOIN top_torrents t ON t.torrent_id = s.torrent_id
                WHERE s.captured_at >= NOW() - (#{days}::integer * INTERVAL '1 day')
                GROUP BY bucket, s.torrent_id
            )
            SELECT bucket, torrent_id, peer_count
            FROM bucketed
            ORDER BY bucket, torrent_id
            """)
    List<TorrentPointRow> selectTorrentPoints(
            @Param("days") int days,
            @Param("bucketHours") int bucketHours
    );

    @Insert("""
            INSERT INTO public.peer_version_snapshot (
                captured_at, dataset, range_hours, ipv4_peers, ipv6_peers
            )
            VALUES (#{capturedAt}, #{dataset}, #{rangeHours}, #{ipv4Peers}, #{ipv6Peers})
            """)
    int insertPeerVersion(
            @Param("capturedAt") OffsetDateTime capturedAt,
            @Param("dataset") String dataset,
            @Param("rangeHours") int rangeHours,
            @Param("ipv4Peers") long ipv4Peers,
            @Param("ipv6Peers") long ipv6Peers
    );

    @Insert("""
            INSERT INTO public.swarm_hot_torrent_snapshot (
                captured_at, torrent_id, peer_count,
                ipv4_peer_count, ipv6_peer_count, rank
            )
            VALUES (
                #{capturedAt}, #{torrentId}, #{peerCount},
                #{ipv4PeerCount}, #{ipv6PeerCount}, #{rank}
            )
            """)
    int insertHotTorrent(
            @Param("capturedAt") OffsetDateTime capturedAt,
            @Param("torrentId") long torrentId,
            @Param("peerCount") long peerCount,
            @Param("ipv4PeerCount") long ipv4PeerCount,
            @Param("ipv6PeerCount") long ipv6PeerCount,
            @Param("rank") int rank
    );

    @Select("""
            SELECT ipv4_peers, ipv6_peers
            FROM public.peer_version_snapshot
            WHERE dataset = #{dataset} AND range_hours = #{rangeHours}
            ORDER BY captured_at DESC
            LIMIT 1
            """)
    PeerVersionSnapshotRow selectLatestPeerVersions(
            @Param("dataset") String dataset,
            @Param("rangeHours") int rangeHours
    );
}
