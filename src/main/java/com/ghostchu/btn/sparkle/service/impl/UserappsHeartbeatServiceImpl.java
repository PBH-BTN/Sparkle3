package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
public class UserappsHeartbeatServiceImpl extends ServiceImpl<UserAppsHeartbeatMapper, UserappsHeartbeat> implements IUserappsHeartbeatService {
    @Value("${sparkle.ping.heartbeat.cleanup-before}")
    private long deleteBefore;
    private final AtomicLong seq = new AtomicLong();

    @Override
    public @NotNull List<UserappsHeartbeat> fetchHeartBeatsByUserAppIdInTimeRange(long userAppId, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .eq("userapp_id", userAppId)
                .ge("last_seen_at", startAt)
                .le("first_seen_at", endAt)
                .orderByDesc("last_seen_at")
        );
    }

    @Override
    public @NotNull List<UserappsHeartbeat> fetchIpHeartbeatRecords(@NotNull String peerIp, @NotNull OffsetDateTime after) {
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .apply("ip <<= {0}::inet", peerIp)
                .gt("last_seen_at", after)
                .orderByDesc("last_seen_at")
        );
    }

    @Transactional
    @Override
    public void onHeartBeat(long userAppId, @NotNull InetAddress ip) {
        var changes = this.baseMapper.upsert(new UserappsHeartbeat()
                .setId(Hashing.sha256().hashString(userAppId + ip.getHostAddress() + System.currentTimeMillis() + seq.incrementAndGet(), StandardCharsets.UTF_8).asLong())
                .setUserappId(userAppId)
                .setIp(ip)
                .setFirstSeenAt(OffsetDateTime.now())
                .setLastSeenAt(OffsetDateTime.now()));
        if (changes <= 0) {
            log.warn("Failed to upsert heartbeat for userAppId: {}, {}", userAppId, ip);
        }
    }

    @Scheduled(cron = "${sparkle.ping.heartbeat.cleanup-cron}")
    @Transactional
    public void deleteOldData() {
        long deleted = 0;
        int lastDelete;
        OffsetDateTime cutoffTime = OffsetDateTime.now().minus(deleteBefore, ChronoUnit.MILLIS);
        do {
            // Select IDs first, then delete by IDs
            List<Long> idsToDelete = this.baseMapper.selectList(new LambdaQueryWrapper<UserappsHeartbeat>()
                    .select(UserappsHeartbeat::getId)
                    .le(UserappsHeartbeat::getLastSeenAt, cutoffTime)
                    .last("LIMIT 1000"))
                    .stream()
                    .map(UserappsHeartbeat::getId)
                    .toList();

            if (idsToDelete.isEmpty()) {
                lastDelete = 0;
            } else {
                lastDelete = this.baseMapper.deleteByIds(idsToDelete);
                deleted += lastDelete;
                if(deleted % 10000 == 0) {
                    log.info("Deleted {} old heartbeat records so far...", deleted);
                }
            }
        } while (lastDelete > 0);
        log.info("Deleted {} old heartbeat records", deleted);
    }
}
