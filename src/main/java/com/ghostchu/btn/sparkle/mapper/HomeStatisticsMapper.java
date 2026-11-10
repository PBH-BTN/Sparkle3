package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.DashboardDailyCountDto;
import com.ghostchu.btn.sparkle.service.dto.DashboardDimensionCountDto;
import com.ghostchu.btn.sparkle.service.dto.DashboardGeoIpDailyCountDto;
import com.ghostchu.btn.sparkle.service.dto.DashboardUserAgentCountDto;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface HomeStatisticsMapper {
    List<DashboardDimensionCountDto> selectTopPeerIds(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("limit") int limit,
            @Param("source") String source,
            @Param("useAggregates") boolean useAggregates
    );

    List<DashboardGeoIpDailyCountDto> selectGeoIpTrend(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("bucketMinutes") int bucketMinutes,
            @Param("limit") int limit,
            @Param("source") String source,
            @Param("useAggregates") boolean useAggregates
    );

    List<DashboardDailyCountDto> selectClientNameTrend(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("bucketMinutes") int bucketMinutes,
            @Param("limit") int limit,
            @Param("source") String source,
            @Param("useAggregates") boolean useAggregates
    );

    List<DashboardUserAgentCountDto> selectActiveUserAgents();

    List<DashboardDimensionCountDto> selectCompanionIpVersions();

    List<DashboardDimensionCountDto> selectCompanionGeoIpDistribution(
            @Param("limit") int limit
    );
}
