package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.sql.Timestamp;
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
public interface IUserappsHeartbeatService extends IService<UserappsHeartbeat> {

    @NotNull List<UserappsHeartbeat> fetchIpHeartbeatRecords(String ip, OffsetDateTime after);

    @Transactional
    void onHeartBeat(long userAppId, @NotNull InetAddress ip);
}
