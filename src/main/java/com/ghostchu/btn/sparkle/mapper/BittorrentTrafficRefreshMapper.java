package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.TrafficRefreshStateRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

public interface BittorrentTrafficRefreshMapper {
    @Select("""
            SELECT backfill_cursor, watermark, ready
            FROM public.dashboard_bittorrent_traffic_refresh_state
            WHERE task = #{task}
            """)
    TrafficRefreshStateRow selectState(@Param("task") String task);

    @Update("""
            ALTER TABLE public.dashboard_ban_connection_traffic
            DISABLE TRIGGER dashboard_ban_connection_traffic_hourly_trigger
            """)
    void disableHourlyTrigger();

    @Update("""
            ALTER TABLE public.dashboard_ban_connection_traffic
            ENABLE TRIGGER dashboard_ban_connection_traffic_hourly_trigger
            """)
    void enableHourlyTrigger();

    @Delete("""
            DELETE FROM public.dashboard_ban_city_hourly_traffic
            WHERE hour < #{cutoff}
            """)
    int deleteBanCityHourlyBefore(@Param("cutoff") OffsetDateTime cutoff);

    @Update("""
            UPDATE public.dashboard_bittorrent_traffic_refresh_state
            SET backfill_cursor = #{cursor}, updated_at = NOW()
            WHERE task = #{task}
            """)
    int updateCursor(
            @Param("task") String task,
            @Param("cursor") OffsetDateTime cursor
    );

    @Update("""
            UPDATE public.dashboard_bittorrent_traffic_refresh_state
            SET backfill_cursor = #{completedAt},
                watermark = #{completedAt},
                ready = TRUE,
                updated_at = NOW()
            WHERE task = #{task}
            """)
    int completeBackfill(
            @Param("task") String task,
            @Param("completedAt") OffsetDateTime completedAt
    );

    @Update("""
            UPDATE public.dashboard_bittorrent_traffic_refresh_state
            SET watermark = #{watermark}, updated_at = NOW()
            WHERE task = #{task}
            """)
    int updateWatermark(
            @Param("task") String task,
            @Param("watermark") OffsetDateTime watermark
    );
}
