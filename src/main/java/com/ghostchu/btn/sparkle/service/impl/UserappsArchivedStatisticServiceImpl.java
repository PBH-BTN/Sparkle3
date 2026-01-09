package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.UserappsArchivedStatistic;
import com.ghostchu.btn.sparkle.mapper.UserappsArchivedStatisticMapper;
import com.ghostchu.btn.sparkle.service.IUserappsArchivedStatisticService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
@Slf4j
public class UserappsArchivedStatisticServiceImpl extends ServiceImpl<UserappsArchivedStatisticMapper, UserappsArchivedStatistic> implements IUserappsArchivedStatisticService {
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void updateArchivedStatistic(long userAppId, long addArchivedToPeerTraffic, long addArchivedFromPeerTraffic, long addArchivedBanHistoryRecords, long addArchivedSwarmTrackerRecords, @NotNull OffsetDateTime lastUpdateAt) {
        this.baseMapper.updateArchivedStatistic(userAppId, addArchivedToPeerTraffic, addArchivedFromPeerTraffic, addArchivedBanHistoryRecords, addArchivedSwarmTrackerRecords, lastUpdateAt);
    }
}
