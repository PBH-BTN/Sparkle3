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

    @Delete("DELETE FROM user_career_events WHERE user_id = #{uid}")
    void deleteEvents(@Param("uid") long uid);
    @Delete("DELETE FROM user_traffic_daily WHERE user_id = #{uid}")
    void deleteTrafficDaily(@Param("uid") long uid);
    @Delete("DELETE FROM user_career_daily WHERE user_id = #{uid}")
    void deleteDaily(@Param("uid") long uid);
    @Delete("DELETE FROM user_career_torrent_time WHERE user_id = #{uid}")
    void deleteTime(@Param("uid") long uid);
}
