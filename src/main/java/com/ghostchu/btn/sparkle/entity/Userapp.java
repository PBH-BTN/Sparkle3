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
import java.net.InetAddress;
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
@TableName("userapp")
@Accessors(chain = true)
public class Userapp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("app_id")
    private String appId;

    @TableField("app_secret")
    private String appSecret;

    @TableField(value = "created_at")
    private OffsetDateTime createdAt;

    @TableField("owner")
    private Long owner;

    @TableField(value = "banned_at")
    private OffsetDateTime bannedAt;

    @TableField("banned_reason")
    private String bannedReason;

    @TableField("comment")
    private String comment;

    @TableField(condition = "delete_at")
    private OffsetDateTime deleteAt;

    @TableField("installation_id")
    private String installationId;

    @TableField("create_ip")
    private InetAddress createIp;

    @TableField("last_seen_at")
    private OffsetDateTime lastSeenAt;
}
