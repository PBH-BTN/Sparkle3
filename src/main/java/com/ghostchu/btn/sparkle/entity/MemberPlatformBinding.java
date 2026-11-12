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
@TableName("member_platform_bindings")
@Accessors(chain = true)
public class MemberPlatformBinding {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String provider;
    @TableField("btn_user_id")
    private Long btnUserId;
    @TableField("external_user_id")
    private String externalUserId;
    @TableField("external_user_name")
    private String externalUserName;
    @TableField("external_user_avatar")
    private String externalUserAvatar;
    private String proof;
    @TableField("bound_at")
    private OffsetDateTime boundAt;
    @TableField("last_synced_at")
    private OffsetDateTime lastSyncedAt;
    @TableField("last_error")
    private String lastError;
}
