package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Getter
@Setter
@ToString
@TableName("community_groups")
@Accessors(chain = true)
public class CommunityGroup {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String slug;
    @TableField("group_type")
    private String groupType;
    @TableField("title_zh")
    private String titleZh;
    @TableField("title_en")
    private String titleEn;
    @TableField("description_zh")
    private String descriptionZh;
    @TableField("description_en")
    private String descriptionEn;
    private String url;
    @TableField("github_organization")
    private String githubOrganization;
    private Boolean enabled;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("updated_by")
    private Long updatedBy;
}
