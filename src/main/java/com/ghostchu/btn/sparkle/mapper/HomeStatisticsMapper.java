package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.DashboardDailyCountDto;
import com.ghostchu.btn.sparkle.service.dto.DashboardDimensionCountDto;
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

    List<DashboardDimensionCountDto> selectTopClientNames(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("limit") int limit,
            @Param("source") String source,
            @Param("useAggregates") boolean useAggregates
    );

    List<DashboardDailyCountDto> selectGeoCityTrend(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("bucketMinutes") int bucketMinutes,
            @Param("source") String source,
            @Param("useAggregates") boolean useAggregates
    );

    List<DashboardDailyCountDto> selectIpVersionTrend(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("bucketMinutes") int bucketMinutes,
            @Param("source") String source,
            @Param("useAggregates") boolean useAggregates
    );

    List<DashboardUserAgentCountDto> selectActiveUserAgents();

    List<DashboardDimensionCountDto> selectCompanionIpVersions();
}
