package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.mapper.UserRelMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import org.apache.ibatis.cursor.Cursor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private UserRelMapper userRelMapper;

    @Override
    public @Nullable UserRel getUserRelByBindUserId(long btnUserId) {
        return userRelMapper.selectOne(new QueryWrapper<UserRel>()
                .eq("bind_user_id", btnUserId));
    }

    /**
     * 用户使用 Github 登录，自动注册或更新用户信息
     *
     * @return 用户实体
     */
    @Transactional
    @Override
    public User userGithubOAuthLogin(@NotNull Authentication authentication, @NotNull InetAddress loginIp, @NotNull Long githubUid,
                                     @NotNull String githubLogin, @NotNull String githubAvatarUrl, @NotNull String githubName, @NotNull String githubEmail) {
        UserRel userRel = userRelMapper.findUserRelByPlatformUserId("github", String.valueOf(githubUid));
        if (userRel != null) {
            User user = baseMapper.selectById(userRel.getBindUserId());
            if (user == null) {
                throw new IllegalStateException("User relation exists but the bound user does not exist, inconsistent database state.");
            }
            user.setNickname(githubName);
            user.setEmail(githubEmail);
            user.setAvatar(githubAvatarUrl);
            user.setLastLoginAt(OffsetDateTime.now());
            baseMapper.updateById(user);
            return user;
        }
        User newUser = new User()
                .setEmail(githubEmail)
                .setNickname(githubName)
                .setAvatar(githubAvatarUrl)
                .setRegisterAt(OffsetDateTime.now())
                .setLastLoginAt(OffsetDateTime.now())
                .setRole("user")
                .setPrivacyLevel(UserPrivacyLevel.MEDIUM);
        boolean success = baseMapper.insert(newUser) != 0;
        if (!success) {
            throw new IllegalStateException("Failed to create new user, the changed records count is 0.");
        }
        UserRel newUserRel = new UserRel()
                .setPlatform("github")
                .setBindUserId(newUser.getId())
                .setBindAt(OffsetDateTime.now())
                .setPlatformUserId(String.valueOf(githubUid))
                .setPlatformUserLogin(githubLogin)
                .setPlatformUserEmail(githubEmail);
        success = userRelMapper.insert(newUserRel) != 0;
        if (!success) {
            throw new IllegalStateException("Failed to create new user relation, the changed records count is 0.");
        }
        return newUser;
    }

    @Override
    public @NotNull List<Long> getSystemUids() {
        return baseMapper.selectList(new QueryWrapper<User>().like("email", "%@sparkle.system")).stream().map(User::getId).toList();
    }

    @Override
    public @NotNull List<Long> fetchAllUserIds() {
        return baseMapper.fetchAllUserIds();
    }

    @Override
    public @NotNull Cursor<User> fetchAllUsers() {
        return baseMapper.fetchAllUsers();
    }
}
