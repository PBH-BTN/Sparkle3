package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.Userapp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
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
    @Nullable
    Userapp loginViaCredential(@NotNull String appId, @NotNull String appSecret);

    @NotNull
    List<Userapp> getUserAppsByUserId(long userId);

    @Nullable Userapp getUserAppByAppId(String appId);

    @NotNull Userapp resetUserApplicationSecret(long id);

    boolean deleteUserAppById(long id);

    @Transactional
    @NotNull Userapp createUserAppForUser(Long userId, String comment, InetAddress creatorIp);
}
