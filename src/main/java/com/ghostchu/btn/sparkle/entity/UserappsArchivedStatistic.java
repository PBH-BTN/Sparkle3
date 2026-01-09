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
@TableName("userapps_archived_statistic")
@Accessors(chain = true)
public class UserappsArchivedStatistic implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "userapp_id", type = IdType.INPUT)
    private Long userappId;

    @TableField("archived_to_peer_traffic")
    private Long archivedToPeerTraffic;

    @TableField("archived_from_peer_traffic")
    private Long archivedFromPeerTraffic;

    @TableField("archived_ban_history_records")
    private Long archivedBanHistoryRecords;

    @TableField("archived_swarm_tracker_records")
    private Long archivedSwarmTrackerRecords;

    @TableField("last_update_at")
    private OffsetDateTime lastUpdateAt;
}
