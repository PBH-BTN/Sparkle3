package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.entity.UserappsArchivedStatistic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface UserappsArchivedStatisticMapper extends BaseMapper<UserappsArchivedStatistic> {

   void updateArchivedStatistic(long userAppId, long archivedToPeerTraffic, long archivedFromPeerTraffic, long archivedBanHistoryRecords, long archivedSwarmTrackerRecords, @NotNull OffsetDateTime lastUpdateAt);

}
