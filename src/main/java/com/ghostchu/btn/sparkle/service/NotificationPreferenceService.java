package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.entity.UserNotificationPreference;
import com.ghostchu.btn.sparkle.mapper.UserNotificationPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    private final UserNotificationPreferenceMapper preferenceMapper;

    public List<NotificationPreference> list(long userId) {
        Map<NotificationCategory, StoredPreference> stored = new HashMap<>();
        preferenceMapper.selectList(
                new QueryWrapper<UserNotificationPreference>().eq("user_id", userId)
        ).forEach(item -> stored.put(
                parse(item.getCategory()),
                new StoredPreference(
                        Boolean.TRUE.equals(item.getInboxEnabled()),
                        Boolean.TRUE.equals(item.getEmailEnabled())
                )
        ));

        List<NotificationPreference> preferences = new ArrayList<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            StoredPreference value = stored.get(category);
            boolean locked = category.isMandatory();
            preferences.add(new NotificationPreference(
                    category,
                    locked || value == null || value.inboxEnabled(),
                    locked || (value == null
                            ? NotificationPreference.defaults(category).emailEnabled()
                            : value.emailEnabled()),
                    locked
            ));
        }
        return preferences;
    }

    public boolean inboxEnabled(long userId, NotificationCategory category) {
        return preference(userId, category).inboxEnabled();
    }

    public boolean emailEnabled(long userId, NotificationCategory category) {
        return preference(userId, category).emailEnabled();
    }

    public NotificationPreference preference(long userId, NotificationCategory category) {
        if (category.isMandatory()) {
            return NotificationPreference.defaults(category);
        }
        UserNotificationPreference row = preferenceMapper.selectOne(
                new QueryWrapper<UserNotificationPreference>()
                        .eq("user_id", userId)
                        .eq("category", category.name())
        );
        if (row == null) {
            return NotificationPreference.defaults(category);
        }
        return new NotificationPreference(
                category,
                Boolean.TRUE.equals(row.getInboxEnabled()),
                Boolean.TRUE.equals(row.getEmailEnabled()),
                false
        );
    }

    @Transactional
    public List<NotificationPreference> update(
            long userId,
            Map<NotificationCategory, Boolean[]> updates
    ) {
        for (NotificationCategory category : NotificationCategory.values()) {
            Boolean[] requested = updates.get(category);
            boolean inbox = category.isMandatory()
                    || requested == null
                    || requested[0] == null
                    || requested[0];
            boolean email = category.isMandatory()
                    || (requested == null || requested[1] == null
                    ? NotificationPreference.defaults(category).emailEnabled()
                    : requested[1]);
            UserNotificationPreference row = preferenceMapper.selectOne(
                    new QueryWrapper<UserNotificationPreference>()
                            .eq("user_id", userId)
                            .eq("category", category.name())
            );
            if (row == null) {
                preferenceMapper.insert(new UserNotificationPreference()
                        .setUserId(userId)
                        .setCategory(category.name())
                        .setInboxEnabled(inbox)
                        .setEmailEnabled(email)
                        .setUpdatedAt(java.time.OffsetDateTime.now()));
            } else {
                row.setInboxEnabled(inbox)
                        .setEmailEnabled(email)
                        .setUpdatedAt(java.time.OffsetDateTime.now());
                preferenceMapper.update(
                        row,
                        new QueryWrapper<UserNotificationPreference>()
                                .eq("user_id", userId)
                                .eq("category", category.name())
                );
            }
        }
        return list(userId);
    }

    private NotificationCategory parse(String value) {
        return NotificationCategory.parse(value).orElse(null);
    }

    private record StoredPreference(boolean inboxEnabled, boolean emailEnabled) {
    }
}
