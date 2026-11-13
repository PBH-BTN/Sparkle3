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
@TableName("app_config_entries")
@Accessors(chain = true)
public class AppConfigEntry {
    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;
    @TableField("config_value")
    private String configValue;
    @TableField("value_type")
    private String valueType;
    @TableField("config_group")
    private String configGroup;
    private String source;
    private Boolean sensitive;
    @TableField("hot_reload")
    private Boolean hotReload;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("updated_by")
    private Long updatedBy;
}
