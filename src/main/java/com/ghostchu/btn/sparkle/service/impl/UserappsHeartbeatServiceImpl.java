package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
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
public class UserappsHeartbeatServiceImpl extends ServiceImpl<UserAppsHeartbeatMapper, UserappsHeartbeat> implements IUserappsHeartbeatService {

    @Transactional
    @Override
    public void onHeartBeat(long userAppId, @NotNull InetAddress ip){
        var changes = this.baseMapper.upsert(new UserappsHeartbeat()
                .setUserappId(userAppId)
                .setIp(ip)
                .setFirstSeenAt(OffsetDateTime.now())
                .setLastSeenAt(OffsetDateTime.now()));
        if(changes <= 0){
            log.warn("Failed to upsert heartbeat for userAppId: {}, {}", userAppId, ip);
        }
    }
}
