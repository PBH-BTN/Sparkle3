package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface ISwarmTrackerService extends IService<SwarmTracker> {

    void syncSwarm(long userAppId, @NotNull List<BtnSwarm> swarms);
}
