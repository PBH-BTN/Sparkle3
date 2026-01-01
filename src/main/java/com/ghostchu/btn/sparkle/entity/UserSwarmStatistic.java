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
@TableName("user_swarm_statistic")
@Accessors(chain = true)
public class UserSwarmStatistic implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.NONE)
    private Long userId;

    @TableField("sent_traffic_self_report")
    private long sentTrafficSelfReport;

    @TableField("received_traffic_self_report")
    private long receivedTrafficSelfReport;

    @TableField("sent_traffic_other_ack")
    private long sentTrafficOtherAck;

    @TableField("received_traffic_other_ack")
    private long receivedTrafficOtherAck;

    @TableField("torrent_count")
    private long torrentCount;

    @TableField("ip_count")
    private long ipCount;

    @TableField("last_update_at")
    private OffsetDateTime lastUpdateAt;
}
