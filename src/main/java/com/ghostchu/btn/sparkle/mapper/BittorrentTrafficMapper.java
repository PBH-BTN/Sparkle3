package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.DashboardTrafficRowDto;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

public interface BittorrentTrafficMapper {
    List<DashboardTrafficRowDto> selectFeatureTraffic(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("peerId") boolean peerId);

    List<DashboardTrafficRowDto> selectBanTraffic(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("days") int days,
            @Param("limit") int limit,
            @Param("useSnapshot") boolean useSnapshot
    );

    List<DashboardTrafficRowDto> selectSwarmTraffic(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("limit") int limit,
            @Param("useSnapshot") boolean useSnapshot
    );

    int deleteSwarmTrafficSnapshot(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );

    int insertSwarmTrafficSnapshot(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );

    int deleteExpiredSwarmTrafficSnapshot(@Param("cutoff") OffsetDateTime cutoff);

    int deleteBanTrafficSnapshot();

    int upsertBanConnectionBackfill(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );

    int upsertBanConnectionsIncremental(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt
    );

    int deleteExpiredBanConnections(@Param("cutoff") OffsetDateTime cutoff);

    int clearBanCityHourlyTraffic();

    int rebuildBanCityHourlyTraffic(@Param("cutoff") OffsetDateTime cutoff);

    int insertBanTrafficSnapshotFromDaily();

    @Select("""
            SELECT to_regclass(#{relation}) IS NOT NULL
               AND has_table_privilege(#{relation}, 'SELECT')
            """)
    Boolean relationReadable(@Param("relation") String relation);
}
