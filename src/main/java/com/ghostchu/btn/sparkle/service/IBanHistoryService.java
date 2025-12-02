package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.entity.BanHistory;
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
public interface IBanHistoryService extends IService<BanHistory> {

    void syncBanHistory(@NotNull String submitterIp, long userAppId, @NotNull List<BtnBan> bans);
}
