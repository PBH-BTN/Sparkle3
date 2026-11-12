package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.net.InetAddress;
import java.util.UUID;

@Getter
@Setter
@TableName("user_messages")
@Accessors(chain = true)
public class UserMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("dispatch_id")
    private UUID dispatchId;
    private Boolean broadcast;
    @TableField("sender_user_id")
    private Long senderUserId;
    @TableField("sender_kind")
    private String senderKind;
    @TableField("sender_display_name")
    private String senderDisplayName;
    @TableField("recipient_user_id")
    private Long recipientUserId;
    private String category;
    private String subject;
    private String content;
    @TableField("inbox_visible")
    private Boolean inboxVisible;
    @TableField("email_requested")
    private Boolean emailRequested;
    @TableField("email_forced")
    private Boolean emailForced;
    @TableField("email_status")
    private String emailStatus;
    @TableField("email_error")
    private String emailError;
    @TableField("email_sent_at")
    private OffsetDateTime emailSentAt;
    @TableField("sender_ip")
    private InetAddress senderIp;
    @TableField("sender_user_agent")
    private String senderUserAgent;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("read_at")
    private OffsetDateTime readAt;
    @TableField("deleted_at")
    private OffsetDateTime deletedAt;
}
