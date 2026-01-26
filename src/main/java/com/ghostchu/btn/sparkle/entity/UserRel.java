package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Getter
@Setter
@ToString
@TableName(value = "user_rel", autoResultMap = true)
@Accessors(chain = true)
public class UserRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("platform")
    private String platform;

    @TableField("platform_user_id")
    private String platformUserId;

    @TableField("platform_user_login")
    private String platformUserLogin;

    @TableField("platform_user_email")
    private String platformUserEmail;

    @TableField("bind_user_id")
    private long bindUserId;

    @TableField(value = "bind_at")
    private OffsetDateTime bindAt;
}
