package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface PointsDynamicMapper {
    @Select("""
            WITH traffic AS (
                SELECT time_bucket(INTERVAL '1 hour', occurred_at) AS bucket,
                       user_id, torrent_id,
                       MAX(GREATEST(torrent_size, 0)) AS torrent_size,
                       SUM(GREATEST(upload_bytes, 0)) AS upload_bytes,
                       SUM(GREATEST(download_bytes, 0)) AS download_bytes
                FROM public.swarm_traffic_delta
                WHERE occurred_at >= #{startAt} AND occurred_at < #{endAt}
                GROUP BY bucket, user_id, torrent_id
            ),
            scored AS (
                SELECT bucket, user_id,
                       SUM(FLOOR(
                           #{uploadPoints}::numeric * upload_bytes * torrent_size
                           / (
                               #{uploadMib}::numeric * 1024 * 1024
                               * #{torrentSizeGib}::numeric * 1024 * 1024 * 1024
                           )
                       ))::bigint AS upload_points,
                       SUM(FLOOR(
                           #{downloadPoints}::numeric * download_bytes * torrent_size
                           / (
                               #{downloadMib}::numeric * 1024 * 1024
                               * #{torrentSizeGib}::numeric * 1024 * 1024 * 1024
                           )
                       ))::bigint AS download_points
                FROM traffic
                GROUP BY bucket, user_id
            )
            SELECT bucket, user_id, upload_points, download_points
            FROM scored
            WHERE upload_points > 0 OR download_points > 0
            """)
    List<Map<String, Object>> selectTrafficEvents(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("uploadPoints") long uploadPoints,
            @Param("uploadMib") long uploadMib,
            @Param("downloadPoints") long downloadPoints,
            @Param("downloadMib") long downloadMib,
            @Param("torrentSizeGib") long torrentSizeGib
    );

    @Select("""
            WITH active AS (
                SELECT time_bucket(INTERVAL '1 hour', s.last_time_seen) AS bucket,
                       s.torrent_id, s.userapps_id, s.peer_ip, s.peer_port,
                       s.peer_id, u.owner AS user_id
                FROM public.swarm_tracker s
                INNER JOIN public.userapp u ON u.id = s.userapps_id
                WHERE s.last_time_seen >= #{startAt} AND s.last_time_seen < #{endAt}
            ),
            torrent_counts AS (
                SELECT bucket, torrent_id, COUNT(DISTINCT peer_ip) AS unique_peers
                FROM active
                GROUP BY bucket, torrent_id
            ),
            top_torrents AS (
                SELECT bucket, torrent_id, unique_peers
                FROM (
                    SELECT bucket, torrent_id, unique_peers,
                           ROW_NUMBER() OVER (
                               PARTITION BY bucket
                               ORDER BY unique_peers DESC, torrent_id ASC
                           ) AS rank
                    FROM torrent_counts
                ) ranked
                WHERE rank <= #{hotPeerTop}
            ),
            user_peers AS (
                SELECT bucket, user_id, torrent_id,
                       COUNT(DISTINCT COALESCE(
                           NULLIF(peer_id, ''),
                           host(peer_ip) || ':' || peer_port
                       )) AS peer_count
                FROM active
                GROUP BY bucket, user_id, torrent_id
            )
            SELECT user_peers.bucket, user_peers.user_id,
                   SUM(user_peers.peer_count * #{hotPeerPoints}::bigint)::bigint AS points
            FROM user_peers
            INNER JOIN top_torrents
                ON top_torrents.bucket = user_peers.bucket
               AND top_torrents.torrent_id = user_peers.torrent_id
            GROUP BY user_peers.bucket, user_peers.user_id
            HAVING SUM(user_peers.peer_count * #{hotPeerPoints}::bigint) > 0
            """)
    List<Map<String, Object>> selectHotPeerEvents(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("hotPeerTop") int hotPeerTop,
            @Param("hotPeerPoints") long hotPeerPoints
    );

    @Select("""
            SELECT last_success_at
            FROM public.point_job_state
            WHERE job_id = #{jobId}
            """)
    OffsetDateTime selectLastSuccess(@Param("jobId") String jobId);

    @Insert("""
            INSERT INTO public.point_job_state (job_id, last_success_at, updated_at)
            VALUES (#{jobId}, #{lastSuccessAt}, NOW())
            ON CONFLICT (job_id) DO UPDATE
            SET last_success_at = EXCLUDED.last_success_at,
                updated_at = NOW()
            """)
    int updateLastSuccess(
            @Param("jobId") String jobId,
            @Param("lastSuccessAt") OffsetDateTime lastSuccessAt
    );
}
