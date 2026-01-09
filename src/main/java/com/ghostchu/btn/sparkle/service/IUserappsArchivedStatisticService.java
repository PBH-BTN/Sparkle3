package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.UserappsArchivedStatistic;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IUserappsArchivedStatisticService extends IService<UserappsArchivedStatistic> {

    @Transactional(propagation = Propagation.MANDATORY)
    void updateArchivedStatistic(long userAppId, long addArchivedToPeerTraffic, long addArchivedFromPeerTraffic, long addArchivedBanHistoryRecords, long addArchivedSwarmTrackerRecords, @NotNull OffsetDateTime lastUpdateAt);
}
