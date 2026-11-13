package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.entity.UserPrivacySettingsEntity;
import com.ghostchu.btn.sparkle.mapper.UserPrivacySettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserPrivacyService {
    private final UserPrivacySettingsMapper settingsMapper;

    public UserPrivacySettings get(long userId) {
        UserPrivacySettingsEntity row = settingsMapper.selectById(userId);
        if (row != null) {
            return map(row);
        }
        UserPrivacySettings defaults = UserPrivacySettings.defaults(userId);
        ensure(userId);
        return defaults;
    }

    public Map<Long, UserPrivacySettings> getAll(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = userIds.stream().distinct().toList();
        Map<Long, UserPrivacySettings> settings = new HashMap<>();
        settingsMapper.selectList(
                new QueryWrapper<UserPrivacySettingsEntity>()
                        .in("user_id", distinctIds)
        ).forEach(row -> settings.put(row.getUserId(), map(row)));
        return settings;
    }

    @Transactional
    public UserPrivacySettings update(
            long userId,
            AvatarVisibility avatarVisibility,
            UsernameVisibility usernameVisibility,
            PointsVisibility pointsVisibility
    ) {
        return update(userId, avatarVisibility, usernameVisibility, pointsVisibility, null);
    }

    @Transactional
    public UserPrivacySettings update(
            long userId,
            AvatarVisibility avatarVisibility,
            UsernameVisibility usernameVisibility,
            PointsVisibility pointsVisibility,
            LifetimeTrafficVisibility lifetimeTrafficVisibility
    ) {
        UserPrivacySettings current = get(userId);
        LifetimeTrafficVisibility visibility = lifetimeTrafficVisibility == null
                ? current.lifetimeTrafficVisibility() : lifetimeTrafficVisibility;
        settingsMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update
                        .LambdaUpdateWrapper<UserPrivacySettingsEntity>()
                        .set(UserPrivacySettingsEntity::getAvatarVisibility,
                                avatarVisibility.name())
                        .set(UserPrivacySettingsEntity::getUsernameVisibility,
                                usernameVisibility.name())
                        .set(UserPrivacySettingsEntity::getPointsVisibility,
                                pointsVisibility.name())
                        .set(UserPrivacySettingsEntity::getLifetimeTrafficVisibility, visibility.name())
                        .set(UserPrivacySettingsEntity::getUpdatedAt,
                                java.time.OffsetDateTime.now())
                        .eq(UserPrivacySettingsEntity::getUserId, userId)
        );
        return new UserPrivacySettings(
                userId,
                avatarVisibility,
                usernameVisibility,
                pointsVisibility,
                current.receiveUserMessages(),
                visibility
        );
    }

    public void ensure(long userId) {
        if (settingsMapper.selectById(userId) == null) {
            settingsMapper.insert(new UserPrivacySettingsEntity()
                    .setUserId(userId)
                    .setAvatarVisibility("BLURRED")
                    .setUsernameVisibility("BLURRED")
                    .setPointsVisibility("SHOW")
                    .setLifetimeTrafficVisibility("HIDDEN")
                    .setReceiveUserMessages(true)
                    .setUpdatedAt(java.time.OffsetDateTime.now()));
        }
    }

    private UserPrivacySettings map(UserPrivacySettingsEntity row) {
        return new UserPrivacySettings(
                row.getUserId(),
                AvatarVisibility.valueOf(row.getAvatarVisibility()),
                UsernameVisibility.valueOf(row.getUsernameVisibility()),
                PointsVisibility.valueOf(row.getPointsVisibility()),
                Boolean.TRUE.equals(row.getReceiveUserMessages()),
                row.getLifetimeTrafficVisibility() == null ? LifetimeTrafficVisibility.HIDDEN
                        : LifetimeTrafficVisibility.valueOf(row.getLifetimeTrafficVisibility())
        );
    }
}
