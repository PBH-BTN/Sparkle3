package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@TableName("mail_inbound_messages")
@Accessors(chain = true)
public class MailInboundMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String mailbox;
    private String uid;
    @TableField("message_id")
    private String messageId;
    @TableField("from_address")
    private String fromAddress;
    @TableField("reply_token")
    private UUID replyToken;
    @TableField("source_message_id")
    private Long sourceMessageId;
    @TableField("result_message_id")
    private Long resultMessageId;
    private String status;
    private String error;
    @TableField("received_at")
    private OffsetDateTime receivedAt;
    @TableField("processed_at")
    private OffsetDateTime processedAt;
}
