package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.DashboardTrafficRowDto;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface BittorrentTrafficMapper {
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

    void createBanTrafficWorkspace();

    int clearBanTrafficWorkspace();

    int prepareBanTrafficWorkspace(@Param("startAt") OffsetDateTime startAt);

    int insertBanTrafficSnapshot();

    void dropBanTrafficWorkspace();
}
