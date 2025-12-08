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
@TableName("torrent")
@Accessors(chain = true)
public class Torrent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("torrent_identifier")
    private String torrentIdentifier;

    @TableField("size")
    private Long size;

    @TableField("private_torrent")
    private Boolean privateTorrent;

    @TableField("info_hash")
    private String infoHash;

    @TableField("torrent_name")
    private String torrentName;

    @TableField("last_seen_at")
    private OffsetDateTime lastSeenAt;
}
