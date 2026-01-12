package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface UserSwarmStatisticMapper extends BaseMapper<UserSwarmStatistic> {
    int updateAllUserSwarmStatistics(@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);

    @NotNull
    List<UserSwarmStatisticTrackRankingDto> calcUsersRanking(double sentTrafficOtherAckWeight,
                                                             double sentTrafficSelfReportWeight,
                                                             double receivedTrafficOtherAckWeight,
                                                             double receivedTrafficSelfReportWeight,
                                                             List<Long> ignoreUserIds);

    @Nullable
    UserSwarmStatisticTrackRankingDto calcUserRanking(long userId,
                                                      double sentTrafficOtherAckWeight,
                                                      double sentTrafficSelfReportWeight,
                                                      double receivedTrafficOtherAckWeight,
                                                      double receivedTrafficSelfReportWeight,
                                                      List<Long> ignoreUserIds);
}
