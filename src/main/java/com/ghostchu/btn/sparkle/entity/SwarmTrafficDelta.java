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
@Accessors(chain = true)
@TableName("swarm_traffic_delta")
public class SwarmTrafficDelta {
    @TableId(value = "occurred_at", type = IdType.INPUT)
    private OffsetDateTime occurredAt;
    @TableField("user_id")
    private long userId;
    @TableField("userapp_id")
    private long userappId;
    @TableField("torrent_id")
    private long torrentId;
    @TableField("torrent_size")
    private long torrentSize;
    @TableField("upload_bytes")
    private long uploadBytes;
    @TableField("download_bytes")
    private long downloadBytes;
}
