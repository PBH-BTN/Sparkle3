package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper;
import com.ghostchu.btn.sparkle.service.BtnAppGeoIpRiskService;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.points.PointAwardBucket;
import com.ghostchu.btn.sparkle.service.points.PointRulesService;
import com.ghostchu.btn.sparkle.service.points.PointToggleService;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserappsHeartbeatServiceImpl
        extends ServiceImpl<UserAppsHeartbeatMapper, UserappsHeartbeat>
        implements IUserappsHeartbeatService {
    @Autowired
    private IPDBManager geoIPManager;
    @Autowired
    private PointsService pointsService;
    @Autowired
    private PointRulesService pointRulesService;
    @Autowired
    private PointToggleService pointToggleService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private BtnRuntimeConfigService btnConfigService;
    @Autowired
    private BtnAppGeoIpRiskService geoIpRisk;

    @Override
    public @NotNull List<UserappsHeartbeat> fetchHeartBeatsByUserAppIdInTimeRange(
            long userAppId,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt
    ) {
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .eq("userapp_id", userAppId)
                .ge("last_seen_at", startAt)
                .le("first_seen_at", endAt)
                .orderByDesc("last_seen_at")
        );
    }

    @Override
    public @NotNull List<UserappsHeartbeat> fetchHeartBeatsByUserAppIdsInTimeRange(
            @NotNull Collection<Long> userAppIds,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt
    ) {
        if (userAppIds.isEmpty()) {
            return List.of();
        }
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .in("userapp_id", userAppIds)
                .ge("last_seen_at", startAt)
                .le("first_seen_at", endAt)
                .orderByDesc("last_seen_at")
        );
    }

    @Override
    public @NotNull List<UserappsHeartbeat> fetchIpHeartbeatRecords(
            @NotNull String peerIp,
            @NotNull OffsetDateTime after
    ) {
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .apply("ip <<= {0}::inet", peerIp)
                .gt("last_seen_at", after)
                .orderByDesc("last_seen_at")
        );
    }

    @Override
    public void onHeartBeat(long userAppId, long userId, @NotNull InetAddress ip) {
        var occurredAt = OffsetDateTime.now();
        var geo = geoIPManager.queryIPDB(ip).geoData();
        var geoData = geo == null ? null : geo.get();
        var region = geoIpRisk.isCollecting() ? BtnAppGeoIpRiskService.Region.from(ip, geoData) : null;
        var id = UUID.randomUUID();
        var heartbeat = new UserappsHeartbeat()
                .setId(id.getMostSignificantBits() ^ id.getLeastSignificantBits())
                .setUserappId(userAppId)
                .setIp(ip)
                .setGeoip(geoData)
                .setFirstSeenAt(occurredAt)
                .setLastSeenAt(occurredAt);

        var points = new PointAwardBucket(userId, "KEEPALIVE", occurredAt);
        if (pointToggleService.isEnabled(PointCategory.ACTIVE_APP)) {
            points.addClaim("ACTIVE_APP_DAILY", pointsService.currentPointDay() + ":" + userAppId,
                    PointCategory.ACTIVE_APP, pointRulesService.points(PointCategory.ACTIVE_APP));
        }
        if (pointToggleService.isEnabled(PointCategory.APP_ONLINE)) {
            points.addAppActivity(userAppId);
        }
        if (pointToggleService.isEnabled(PointCategory.KEEPALIVE)) {
            points.addContribution(PointCategory.KEEPALIVE, pointRulesService.points(PointCategory.KEEPALIVE), 1, 1);
        }

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            baseMapper.upsert(heartbeat);
            if (points.hasWork()) {
                pointsService.commit(points);
            }
            if (region != null) {
                geoIpRisk.record(List.of(new BtnAppGeoIpRiskService.Receipt(userAppId, occurredAt, region)));
            }
        });
    }

    @MonitoredScheduledTask(
            id = "heartbeat-cleanup",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.heartbeat-cleanup.name",
            descriptionKey = "admin.tasks.items.heartbeat-cleanup.description",
            expression = "${sparkle.ping.heartbeat.cleanup-cron}"
    )
    public void deleteOldData() {
        long deleted = 0;
        int lastDelete;
        long deleteBefore = btnConfigService.longValue("heartbeat", "cleanupBefore");
        OffsetDateTime cutoffTime = OffsetDateTime.now()
                .minus(deleteBefore, ChronoUnit.MILLIS);
        for (int batch = 0; batch < 20; batch++) {
            lastDelete = baseMapper.deleteExpiredBatch(cutoffTime, 1000);
            deleted += lastDelete;
            if (lastDelete < 1000) break;
        }
        log.info("Deleted {} old heartbeat records", deleted);
    }
}
