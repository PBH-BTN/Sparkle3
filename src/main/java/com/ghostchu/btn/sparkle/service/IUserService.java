package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IUserService extends IService<User> {

    @Nullable UserRel getUserRelByBindUserId(long btnUserId);

    @Transactional
    User userGithubOAuthLogin(@NotNull Authentication authentication, @NotNull InetAddress loginIp, @NotNull Long githubUid,
                              @NotNull String githubLogin, @NotNull String githubAvatarUrl, @NotNull String githubName, @NotNull String githubEmail);
}
