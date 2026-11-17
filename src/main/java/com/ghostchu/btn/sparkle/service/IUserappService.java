package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.Userapp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public interface IUserappService extends IService<Userapp> {
    Page<Userapp> pageByBanCount(Page<Userapp> page, QueryWrapper<Userapp> filters,
                                OffsetDateTime start, OffsetDateTime end);

    @Nullable
    Userapp loginViaCredential(@NotNull String appId, @NotNull String appSecret, @Nullable String installationId, @NotNull InetAddress loginIp, @Nullable String userAgent);

    @NotNull
    List<Userapp> getUserAppsByUserId(long userId);

    @Nullable Userapp getUserAppByAppId(String appId);

    @NotNull Userapp resetUserApplicationSecret(long id);

    boolean deleteUserAppById(long id);

    @NotNull Userapp createUserAppForUser(Long userId, String comment, InetAddress creatorIp);

    void updateUserAppLastSeen(long id, String userAgent);
}
