package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.btn.sparkle.converter.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName(value = "ai_moderation_jobs", autoResultMap = true)
@Accessors(chain = true)
public class AiModerationJob {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("content_type")
    private String contentType;
    @TableField("content_ref_id")
    private Long contentRefId;
    @TableField("content_version")
    private Long contentVersion;
    @TableField("subject_user_id")
    private Long subjectUserId;
    @TableField("text_content")
    private String textContent;
    @TableField("image_url")
    private String imageUrl;
    private String status;
    @TableField("attempt_count")
    private Integer attemptCount;
    @TableField("max_attempts")
    private Integer maxAttempts;
    @TableField("provider_model")
    private String providerModel;
    @TableField(value = "result_json", typeHandler = JsonbStringTypeHandler.class)
    private String resultJson;
    @TableField("rejection_reason")
    private String rejectionReason;
    @TableField("last_error")
    private String lastError;
    @TableField("notification_sent_at")
    private OffsetDateTime notificationSentAt;
    @TableField("next_attempt_at")
    private OffsetDateTime nextAttemptAt;
    @TableField("locked_by")
    private String lockedBy;
    @TableField("locked_at")
    private OffsetDateTime lockedAt;
    @TableField("started_at")
    private OffsetDateTime startedAt;
    @TableField("completed_at")
    private OffsetDateTime completedAt;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
