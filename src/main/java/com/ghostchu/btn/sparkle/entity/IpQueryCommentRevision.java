package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName(value = "ip_query_comment_revisions", autoResultMap = true)
@Accessors(chain = true)
public class IpQueryCommentRevision {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("comment_id")
    private Long commentId;
    private Integer revision;
    private String content;
    @TableField("moderation_status")
    private String moderationStatus;
    @TableField("moderation_reason")
    private String moderationReason;
    @TableField("editor_user_id")
    private Long editorUserId;
    @TableField("editor_ip")
    private InetAddress editorIp;
    @TableField("editor_user_agent")
    private String editorUserAgent;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
