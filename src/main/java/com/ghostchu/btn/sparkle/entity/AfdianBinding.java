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
@TableName("afdian_bindings")
@Accessors(chain = true)
public class AfdianBinding {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("btn_user_id")
    private Long btnUserId;
    @TableField("afdian_user_id")
    private String afdianUserId;
    @TableField("afdian_name")
    private String afdianName;
    @TableField("afdian_avatar")
    private String afdianAvatar;
    @TableField("bound_order_no")
    private String boundOrderNo;
    @TableField("bound_at")
    private OffsetDateTime boundAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("last_synced_at")
    private OffsetDateTime lastSyncedAt;
    @TableField("last_error")
    private String lastError;
}
