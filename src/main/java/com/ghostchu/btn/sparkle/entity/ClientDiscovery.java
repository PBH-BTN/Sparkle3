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
@Accessors(chain = true)
@TableName("client_discovery")
public class ClientDiscovery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "hash", type = IdType.INPUT)
    private Long hash;

    @TableField("peer_id")
    private String peerId;

    @TableField("peer_client_name")
    private String peerClientName;

    @TableField(value = "found_at")
    private OffsetDateTime foundAt;

    @TableField("found_userapps_id")
    private Long foundUserappsId;

    @TableField("client_type")
    private String clientType;

    @TableField("client_semver")
    private String clientSemver;
}
