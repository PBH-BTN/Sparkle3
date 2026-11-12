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
@TableName("member_platform_sync_runs")
@Accessors(chain = true)
public class MemberPlatformSyncRun {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String provider;
    private String status;
    @TableField("started_at")
    private OffsetDateTime startedAt;
    @TableField("completed_at")
    private OffsetDateTime completedAt;
    @TableField("pages_fetched")
    private Integer pagesFetched;
    @TableField("records_seen")
    private Integer recordsSeen;
    @TableField("inserted_count")
    private Integer insertedCount;
    @TableField("updated_count")
    private Integer updatedCount;
    @TableField("unchanged_count")
    private Integer unchangedCount;
    @TableField("missing_count")
    private Integer missingCount;
    @TableField("error_message")
    private String errorMessage;
}
