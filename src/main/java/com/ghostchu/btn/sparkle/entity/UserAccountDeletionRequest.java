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
@TableName("user_account_deletion_request")
@Accessors(chain = true)
public class UserAccountDeletionRequest {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private String status;
    @TableField("requested_at")
    private OffsetDateTime requestedAt;
    @TableField("cooling_ends_at")
    private OffsetDateTime coolingEndsAt;
    @TableField("cancelled_at")
    private OffsetDateTime cancelledAt;
    @TableField("completed_at")
    private OffsetDateTime completedAt;
    @TableField("cancellation_reason")
    private String cancellationReason;
    @TableField("last_error")
    private String lastError;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
