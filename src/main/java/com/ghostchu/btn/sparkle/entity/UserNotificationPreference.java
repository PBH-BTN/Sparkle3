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
@TableName("user_notification_preferences")
@Accessors(chain = true)
public class UserNotificationPreference {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private String category;
    @TableField("inbox_enabled")
    private Boolean inboxEnabled;
    @TableField("email_enabled")
    private Boolean emailEnabled;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
