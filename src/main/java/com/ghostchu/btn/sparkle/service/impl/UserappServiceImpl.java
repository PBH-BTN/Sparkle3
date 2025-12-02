package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.mapper.UserappMapper;
import com.ghostchu.btn.sparkle.service.IUserappService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
public class UserappServiceImpl extends ServiceImpl<UserappMapper, Userapp> implements IUserappService {
    @Qualifier("stringLongRedisTemplate")
    @Autowired
    private RedisTemplate<String, Long> userAppsRedisTemplate;

    @Nullable
    public Userapp loginViaCredential(@NotNull String appId, @NotNull String appSecret) {
        Userapp loggedInUserApp = baseMapper.selectOne(new QueryWrapper<Userapp>().eq("app_id", appId).eq("app_secret", appSecret));
        userAppsRedisTemplate.opsForValue().set("sparkle:userapps:lastaccess:" + loggedInUserApp.getId(), System.currentTimeMillis());
        return loggedInUserApp;
    }

    @Nullable
    public Long getUserAppLastAccess(long userAppId) {
        return userAppsRedisTemplate.opsForValue().get("sparkle:userapps:lastaccess:" + userAppId);
    }

    @Override
    public @NotNull List<Userapp> getUserAppsByUserId(long userId) {
        return baseMapper.selectList(new QueryWrapper<Userapp>().eq("owner", userId));
    }

    @Override
    public @Nullable Userapp getUserAppByAppId(String appId) {
        return baseMapper.selectOne(new QueryWrapper<Userapp>().eq("app_id", appId));
    }

    @Override
    @Transactional
    public @NotNull Userapp resetUserApplicationSecret(long id) {
        Userapp userapp = baseMapper.selectById(id);
        if (userapp == null)
            throw new IllegalStateException("Userapp for reset application secret not found");
        userapp.setAppSecret(UUID.randomUUID().toString());
        if (baseMapper.updateById(userapp) <= 0)
            throw new IllegalStateException("Failed to reset userapp secret");
        return userapp;
    }

    @Override
    @Transactional
    public boolean deleteUserAppById(long id) {
        return baseMapper.deleteById(id) > 0;
    }


    @Transactional
    @Override
    public @NotNull Userapp createUserAppForUser(Long userId, String comment, InetAddress creatorIp) {
        Userapp userapp = new Userapp();
        userapp.setOwner(userId);
        userapp.setAppId(UUID.randomUUID().toString());
        userapp.setAppSecret(UUID.randomUUID().toString());
        userapp.setComment(comment);
        userapp.setCreateIp(creatorIp);
        userapp.setCreatedAt(OffsetDateTime.now());
        if (baseMapper.insert(userapp) <= 0)
            throw new IllegalStateException("Failed to create userapp for user");
        return userapp;
    }
}
