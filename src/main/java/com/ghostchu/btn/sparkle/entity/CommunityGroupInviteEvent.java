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
@TableName("community_group_invite_events")
@Accessors(chain = true)
public class CommunityGroupInviteEvent {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("group_id")
    private Long groupId;
    @TableField("github_login")
    private String githubLogin;
    private String result;
    private String message;
    @TableField("created_at")
    private OffsetDateTime createdAt;
}
