package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface UserCareerMapper {
    @Select("""
            SELECT kind, SUM(amount)::text AS total,
                   COALESCE(SUM(amount) FILTER (WHERE day >= (now() AT TIME ZONE 'UTC')::date - 6),0)::text AS recent
            FROM user_career_daily WHERE user_id = #{uid} GROUP BY kind
            """)
    List<Map<String, String>> counts(@Param("uid") long uid);

    @Select("""
            SELECT COALESCE(SUM(extract(epoch FROM upper(period)-lower(period))) FILTER (WHERE mode.seed),0)::text AS seeding,
                   COALESCE(SUM(extract(epoch FROM upper(period)-lower(period))) FILTER (WHERE NOT mode.seed),0)::text AS downloading
            FROM user_career_torrent_time t
            CROSS JOIN LATERAL (VALUES (TRUE,t.seeding),(FALSE,t.downloading-t.seeding)) mode(seed,ranges)
            CROSS JOIN LATERAL unnest(mode.ranges) period
            WHERE t.user_id = #{uid}
            """)
    Map<String, String> durations(@Param("uid") long uid);

    @Select("""
            SELECT reporter_id, observed_at FROM user_career_events e JOIN "user" u ON u.id = e.reporter_id
            WHERE user_id = #{uid} AND kind = 'observation'
            ORDER BY observed_at DESC, reporter_id LIMIT 1
            """)
    Observation latest(@Param("uid") long uid);

    record Observation(Long reporterId, java.time.OffsetDateTime observedAt) { }

    @Delete("DELETE FROM user_career_events WHERE user_id = #{uid}")
    void deleteEvents(@Param("uid") long uid);
    @Delete("DELETE FROM user_career_daily WHERE user_id = #{uid}")
    void deleteDaily(@Param("uid") long uid);
    @Delete("DELETE FROM user_career_torrent_time WHERE user_id = #{uid}")
    void deleteTime(@Param("uid") long uid);
}
