package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@ToString
@TableName("community_group_github_teams")
@Accessors(chain = true)
public class CommunityGroupTeam {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("group_id")
    private Long groupId;
    @TableField("team_slug")
    private String teamSlug;
    @TableField("team_name")
    private String teamName;
    @TableField("sort_order")
    private Integer sortOrder;
}
