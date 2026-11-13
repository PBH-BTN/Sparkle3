package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface PointsMapper {
    @Select("""
            SELECT public.sparkle_touch_point_app_activity(
                #{userappId}, #{userId}, #{occurredAt},
                CAST(#{onlineGapMinutes} AS INTEGER)
            )
            """)
    Long touchAppActivity(
            @Param("userappId") long userappId,
            @Param("userId") long userId,
            @Param("occurredAt") OffsetDateTime occurredAt,
            @Param("onlineGapMinutes") int onlineGapMinutes
    );

    @Select("""
            SELECT daily_unique, new_unique
            FROM public.sparkle_mark_submitted_ips(
                #{userId}, #{pointDay}, CAST(#{ruleMatchedIps} AS jsonb),
                CAST(#{allIps} AS jsonb)
            )
            """)
    List<Map<String, Object>> markSubmittedIps(
            @Param("userId") long userId,
            @Param("pointDay") LocalDate pointDay,
            @Param("ruleMatchedIps") String ruleMatchedIps,
            @Param("allIps") String allIps
    );

    @Select("""
            SELECT result_category AS category, result_points AS points
            FROM public.sparkle_claim_point_awards(
                #{userId}, CAST(#{payload} AS jsonb)
            )
            """)
    List<Map<String, Object>> claimAwards(
            @Param("userId") long userId,
            @Param("payload") String payload
    );

    @Select("""
            SELECT result_category AS category, result_points AS awarded
            FROM public.sparkle_apply_point_categories(
                #{userId}, #{pointDay}, CAST(#{payload} AS jsonb)
            )
            """)
    List<Map<String, Object>> applyDailyCategories(
            @Param("userId") long userId,
            @Param("pointDay") LocalDate pointDay,
            @Param("payload") String payload
    );

    @Update("""
            UPDATE public."user"
            SET score_bytes = score_bytes + #{points}
            WHERE id = #{userId}
            """)
    int addBalance(
            @Param("userId") long userId,
            @Param("points") long points
    );

    @Select("""
            SELECT score_bytes FROM public."user"
            WHERE id = #{userId}
            FOR UPDATE
            """)
    Long lockUserBalance(@Param("userId") long userId);

    @Update("""
            UPDATE public."user" SET score_bytes = #{balance}
            WHERE id = #{userId}
            """)
    int setBalance(
            @Param("userId") long userId,
            @Param("balance") long balance
    );

    @Insert("""
            INSERT INTO public.points_ledger (
                user_id, event_id, occurred_at, event_type, points,
                fixed_points, one_time_points, dynamic_points,
                manual_points, details
            )
            VALUES (
                #{userId}, #{eventId}, #{occurredAt}, #{eventType}, #{points},
                #{fixedPoints}, #{oneTimePoints}, #{dynamicPoints},
                #{manualPoints}, CAST(#{details} AS jsonb)
            )
            """)
    int insertLedger(
            @Param("userId") long userId,
            @Param("eventId") Object eventId,
            @Param("occurredAt") OffsetDateTime occurredAt,
            @Param("eventType") String eventType,
            @Param("points") long points,
            @Param("fixedPoints") long fixedPoints,
            @Param("oneTimePoints") long oneTimePoints,
            @Param("dynamicPoints") long dynamicPoints,
            @Param("manualPoints") long manualPoints,
            @Param("details") String details
    );

    @Select("""
            SELECT
                COALESCE(SUM(fixed_points) FILTER (WHERE occurred_at >= #{dayStart}), 0)
                    AS fixed_today,
                COALESCE(SUM(one_time_points) FILTER (WHERE occurred_at >= #{dayStart}), 0)
                    AS once_today,
                COALESCE(SUM(dynamic_points) FILTER (WHERE occurred_at >= #{dayStart}), 0)
                    AS dynamic_today,
                COALESCE(SUM(fixed_points) FILTER (WHERE occurred_at >= #{last24Hours}), 0)
                    AS fixed_24h,
                COALESCE(SUM(one_time_points) FILTER (WHERE occurred_at >= #{last24Hours}), 0)
                    AS once_24h,
                COALESCE(SUM(dynamic_points) FILTER (WHERE occurred_at >= #{last24Hours}), 0)
                    AS dynamic_24h
            FROM public.points_ledger
            WHERE user_id = #{userId} AND occurred_at >= #{last24Hours}
            """)
    Map<String, Object> selectAggregate(
            @Param("userId") long userId,
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("last24Hours") OffsetDateTime last24Hours
    );

    @Select("""
            SELECT category, awarded
            FROM public.point_daily_category
            WHERE user_id = #{userId} AND point_day = #{pointDay}
            """)
    List<Map<String, Object>> selectDailyCategories(
            @Param("userId") long userId,
            @Param("pointDay") LocalDate pointDay
    );

    @Select("""
            SELECT COUNT(*)
            FROM public.points_ledger
            WHERE user_id = #{userId}
            """)
    long countLedger(@Param("userId") long userId);

    @Select("""
            SELECT id, occurred_at,
                   CASE event_type
                       WHEN 'KEEPALIVE_BATCH' THEN 'KEEPALIVE'
                       ELSE event_type
                   END AS event_type,
                   points, fixed_points, one_time_points, dynamic_points,
                   manual_points, details::text AS details
            FROM public.points_ledger
            WHERE user_id = #{userId}
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> selectLedger(
            @Param("userId") long userId,
            @Param("size") int size,
            @Param("offset") long offset
    );
}
