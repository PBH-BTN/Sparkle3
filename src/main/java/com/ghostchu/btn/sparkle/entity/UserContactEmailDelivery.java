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
@TableName("user_contact_email_deliveries")
@Accessors(chain = true)
public class UserContactEmailDelivery {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("sender_user_id")
    private Long senderUserId;
    @TableField("sender_nickname")
    private String senderNickname;
    @TableField("sender_email")
    private String senderEmail;
    @TableField("recipient_user_id")
    private Long recipientUserId;
    @TableField("recipient_nickname")
    private String recipientNickname;
    @TableField("recipient_email")
    private String recipientEmail;
    private String subject;
    @TableField("sender_ip")
    private String senderIp;
    @TableField("sender_user_agent")
    private String senderUserAgent;
    @TableField("sent_at")
    private OffsetDateTime sentAt;
}
