package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.Rule;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IUserSwarmStatisticsService extends IService<UserSwarmStatistic> {

    @NotNull List<UserSwarmStatisticTrackRankingDto> getUsersRanking();

    @Nullable UserSwarmStatisticTrackRankingDto getUserRanking(long userId);
}
