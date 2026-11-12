package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationPreferenceService {
    private final JdbcTemplate jdbcTemplate;

    public NotificationPreferenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NotificationPreference> list(long userId) {
        Map<NotificationCategory, StoredPreference> stored = new HashMap<>();
        jdbcTemplate.query("""
                SELECT category, inbox_enabled, email_enabled
                FROM user_notification_preferences
                WHERE user_id = ?
                """, (resultSet, rowNum) -> stored.put(
                parse(resultSet.getString("category")),
                new StoredPreference(
                        resultSet.getBoolean("inbox_enabled"),
                        resultSet.getBoolean("email_enabled")
                )
        ), userId);

        List<NotificationPreference> preferences = new ArrayList<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            StoredPreference value = stored.get(category);
            boolean locked = category.isMandatory();
            preferences.add(new NotificationPreference(
                    category,
                    locked || value == null || value.inboxEnabled(),
                    locked || value == null || value.emailEnabled(),
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
        List<StoredPreference> rows = jdbcTemplate.query("""
                SELECT inbox_enabled, email_enabled
                FROM user_notification_preferences
                WHERE user_id = ? AND category = ?
                """, (resultSet, rowNum) -> new StoredPreference(
                resultSet.getBoolean("inbox_enabled"),
                resultSet.getBoolean("email_enabled")
        ), userId, category.name());
        if (rows.isEmpty()) {
            return NotificationPreference.defaults(category);
        }
        StoredPreference value = rows.getFirst();
        return new NotificationPreference(
                category,
                value.inboxEnabled(),
                value.emailEnabled(),
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
                    || requested == null
                    || requested[1] == null
                    || requested[1];
            jdbcTemplate.update("""
                    INSERT INTO user_notification_preferences (
                        user_id, category, inbox_enabled, email_enabled, updated_at
                    )
                    VALUES (?, ?, ?, ?, now())
                    ON CONFLICT (user_id, category) DO UPDATE
                    SET inbox_enabled = EXCLUDED.inbox_enabled,
                        email_enabled = EXCLUDED.email_enabled,
                        updated_at = now()
                    """, userId, category.name(), inbox, email);
        }
        return list(userId);
    }

    private NotificationCategory parse(String value) {
        return NotificationCategory.parse(value).orElse(null);
    }

    private record StoredPreference(boolean inboxEnabled, boolean emailEnabled) {
    }
}
