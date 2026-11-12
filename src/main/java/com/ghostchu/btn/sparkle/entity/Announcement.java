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
@TableName("announcements")
@Accessors(chain = true)
public class Announcement {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String level;
    private String placement;
    @TableField("title_zh")
    private String titleZh;
    @TableField("title_en")
    private String titleEn;
    @TableField("content_zh")
    private String contentZh;
    @TableField("content_en")
    private String contentEn;
    private Boolean enabled;
    private Boolean dismissible;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("starts_at")
    private OffsetDateTime startsAt;
    @TableField("ends_at")
    private OffsetDateTime endsAt;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("updated_by")
    private Long updatedBy;
}
