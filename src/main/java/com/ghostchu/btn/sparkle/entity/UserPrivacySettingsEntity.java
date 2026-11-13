package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("user_privacy_settings")
@Accessors(chain = true)
public class UserPrivacySettingsEntity {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    @TableField("avatar_visibility")
    private String avatarVisibility;
    @TableField("username_visibility")
    private String usernameVisibility;
    @TableField("points_visibility")
    private String pointsVisibility;
    @TableField("receive_user_messages")
    private Boolean receiveUserMessages;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
