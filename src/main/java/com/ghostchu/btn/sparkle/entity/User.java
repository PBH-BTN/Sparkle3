package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.util.SuspensionStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.ibatis.type.ArrayTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * <p>
 *
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Getter
@Setter
@ToString
@TableName(value = "\"user\"", autoResultMap = true)
@Accessors(chain = true)
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("email")
    private String email;

    @TableField("avatar")
    private String avatar;

    @TableField("nickname")
    private String nickname;

    @TableField(value = "register_at")
    private OffsetDateTime registerAt;

    @TableField(value = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @TableField(value = "banned_at", updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime bannedAt;

    @TableField(value = "banned_reason", updateStrategy = FieldStrategy.NEVER)
    private String bannedReason;

    @TableField(value = "unban_at", updateStrategy = FieldStrategy.NEVER)
    private OffsetDateTime unbanAt;

    @TableField(value = "roles", jdbcType = JdbcType.ARRAY, typeHandler = ArrayTypeHandler.class,
            updateStrategy = FieldStrategy.NEVER)
    private String[] roles;

    // Retain the old field for sessions serialized before the roles migration.
    @TableField(exist = false)
    private String role;

    public List<String> roleNames() {
        if (roles != null) {
            return List.of(roles);
        }
        String legacy = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        return switch (legacy) {
            case "admin", "role_admin" -> List.of("user", "admin");
            case "moderator", "role_moderator" -> List.of("user", "moderator");
            case "supporter" -> List.of("user", "supporter");
            default -> List.of("user");
        };
    }

    public String getRole() {
        var names = roleNames();
        if (names.contains("admin")) return "admin";
        if (names.contains("moderator")) return "moderator";
        if (names.contains("supporter")) return "supporter";
        return "user";
    }

    @TableField("privacy_level")
    private UserPrivacyLevel privacyLevel;

    @TableField("score_bytes")
    private Long scoreBytes;

    @TableField("nickname_moderation_status")
    private String nicknameModerationStatus;

    @TableField("avatar_moderation_status")
    private String avatarModerationStatus;

    @TableField("nickname_moderation_reason")
    private String nicknameModerationReason;

    @TableField("avatar_moderation_reason")
    private String avatarModerationReason;

    @TableField("nickname_review_generation")
    private Long nicknameReviewGeneration;

    @TableField("avatar_review_generation")
    private Long avatarReviewGeneration;

    @NotNull
    public String getAvatarUrl(){
        return "/user/avatar/" + this.id;
    }

    public boolean isNicknamePubliclyVisible() {
        return nicknameModerationStatus == null
                || "APPROVED".equals(nicknameModerationStatus);
    }

    public boolean isAvatarPubliclyVisible() {
        return avatarModerationStatus == null
                || "APPROVED".equals(avatarModerationStatus);
    }

    public boolean isSuspended() {
        return SuspensionStatus.isActive(bannedAt, unbanAt, OffsetDateTime.now());
    }
}
