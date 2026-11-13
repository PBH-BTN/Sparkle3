package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.btn.sparkle.converter.CidrTypeHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName(value = "ip_query_comments", autoResultMap = true)
@Accessors(chain = true)
public class IpQueryComment {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "network", typeHandler = CidrTypeHandler.class)
    private String network;
    @TableField("parent_comment_id")
    private Long parentCommentId;
    @TableField("author_user_id")
    private Long authorUserId;
    private String content;
    @TableField("moderation_status")
    private String moderationStatus;
    @TableField("moderation_reason")
    private String moderationReason;
    @TableField("review_generation")
    private Long reviewGeneration;
    private Integer revision;
    @TableField("author_ip")
    private InetAddress authorIp;
    @TableField("author_user_agent")
    private String authorUserAgent;
    @TableField("reply_notified_at")
    private OffsetDateTime replyNotifiedAt;
    @TableField("anonymized_at")
    private OffsetDateTime anonymizedAt;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("deleted_at")
    private OffsetDateTime deletedAt;
}
