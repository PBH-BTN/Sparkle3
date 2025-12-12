package com.ghostchu.btn.sparkle.service.impl;

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
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

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

    @Override
    public @NotNull List<UserappsHeartbeat> fetchIpHeartbeatRecords(@NotNull String peerIp, @NotNull Timestamp after) {
        return this.baseMapper.selectList(new QueryWrapper<UserappsHeartbeat>()
                .apply("ip <<= '{0}'::inet", peerIp)
                .gt("last_seen_at", OffsetDateTime.ofInstant(after.toInstant(), OffsetDateTime.now().getOffset()))
                .orderByDesc("last_seen_at")
        );
    }

    @Transactional
    @Override
    public void onHeartBeat(long userAppId, @NotNull InetAddress ip) {
        var changes = this.baseMapper.upsert(new UserappsHeartbeat()
                .setId(Hashing.sha256().hashString(userAppId + ip.getHostAddress(), StandardCharsets.UTF_8).asLong())
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
        var deleted = this.baseMapper.delete(new QueryWrapper<UserappsHeartbeat>()
                .le("last_seen_at", OffsetDateTime.now().minusSeconds(deleteBefore / 1000)));
        if (deleted > 0) {
            log.info("Deleted {} expired heartbeats", deleted);
        }
    }
}
