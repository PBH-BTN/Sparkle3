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
@TableName("navigation_items")
@Accessors(chain = true)
public class NavigationItem {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("built_in")
    private Boolean builtIn;
    @TableField("label_zh")
    private String labelZh;
    @TableField("label_en")
    private String labelEn;
    private String href;
    private String icon;
    @TableField("sort_order")
    private Integer sortOrder;
    private Boolean enabled;
    @TableField("open_in_new_tab")
    private Boolean openInNewTab;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("updated_by")
    private Long updatedBy;
}
