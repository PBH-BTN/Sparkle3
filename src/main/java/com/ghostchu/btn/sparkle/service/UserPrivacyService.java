package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserPrivacyService {
    private static final RowMapper<UserPrivacySettings> MAPPER = (resultSet, rowNum) ->
            new UserPrivacySettings(
                    resultSet.getLong("user_id"),
                    AvatarVisibility.valueOf(resultSet.getString("avatar_visibility")),
                    UsernameVisibility.valueOf(resultSet.getString("username_visibility")),
                    PointsVisibility.valueOf(resultSet.getString("points_visibility")),
                    resultSet.getBoolean("receive_user_messages")
            );

    private final JdbcTemplate jdbcTemplate;

    public UserPrivacyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserPrivacySettings get(long userId) {
        List<UserPrivacySettings> rows = jdbcTemplate.query("""
                SELECT user_id, avatar_visibility, username_visibility,
                       points_visibility, receive_user_messages
                FROM user_privacy_settings
                WHERE user_id = ?
                """, MAPPER, userId);
        if (!rows.isEmpty()) {
            return rows.getFirst();
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
        String placeholders = String.join(", ", java.util.Collections.nCopies(distinctIds.size(), "?"));
        Map<Long, UserPrivacySettings> settings = new HashMap<>();
        jdbcTemplate.query("""
                SELECT user_id, avatar_visibility, username_visibility,
                       points_visibility, receive_user_messages
                FROM user_privacy_settings
                WHERE user_id IN (%s)
                """.formatted(placeholders), MAPPER, distinctIds.toArray()).forEach(row ->
                settings.put(row.userId(), row)
        );
        return settings;
    }

    @Transactional
    public UserPrivacySettings update(
            long userId,
            AvatarVisibility avatarVisibility,
            UsernameVisibility usernameVisibility,
            PointsVisibility pointsVisibility
    ) {
        UserPrivacySettings current = get(userId);
        jdbcTemplate.update("""
                INSERT INTO user_privacy_settings (
                    user_id, avatar_visibility, username_visibility,
                    points_visibility, receive_user_messages, updated_at
                )
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE
                SET avatar_visibility = EXCLUDED.avatar_visibility,
                    username_visibility = EXCLUDED.username_visibility,
                    points_visibility = EXCLUDED.points_visibility,
                    updated_at = now()
                """,
                userId,
                avatarVisibility.name(),
                usernameVisibility.name(),
                pointsVisibility.name(),
                current.receiveUserMessages()
        );
        return new UserPrivacySettings(
                userId,
                avatarVisibility,
                usernameVisibility,
                pointsVisibility,
                current.receiveUserMessages()
        );
    }

    public void ensure(long userId) {
        jdbcTemplate.update("""
                INSERT INTO user_privacy_settings (
                    user_id, avatar_visibility, username_visibility,
                    points_visibility, receive_user_messages
                )
                VALUES (?, 'BLURRED', 'BLURRED', 'SHOW', TRUE)
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
    }
}
