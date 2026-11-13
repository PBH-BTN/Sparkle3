package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@TableName("github_sync_target_sources")
@Accessors(chain = true)
public class GithubSyncTargetSource {
    @TableId(value = "target_id", type = IdType.INPUT)
    private Long targetId;
    @TableField("source_id")
    private String sourceId;
}
