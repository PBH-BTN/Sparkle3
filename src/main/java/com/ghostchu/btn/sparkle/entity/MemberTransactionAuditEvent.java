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
@TableName(value = "member_transaction_audit_events", autoResultMap = true)
@Accessors(chain = true)
public class MemberTransactionAuditEvent {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("sync_run_id")
    private Long syncRunId;
    private String provider;
    @TableField("external_transaction_id")
    private String externalTransactionId;
    @TableField("event_type")
    private String eventType;
    @TableField(value = "changed_fields", typeHandler = JsonbStringTypeHandler.class)
    private String changedFields;
    @TableField(value = "previous_data", typeHandler = JsonbStringTypeHandler.class)
    private String previousData;
    @TableField(value = "current_data", typeHandler = JsonbStringTypeHandler.class)
    private String currentData;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
