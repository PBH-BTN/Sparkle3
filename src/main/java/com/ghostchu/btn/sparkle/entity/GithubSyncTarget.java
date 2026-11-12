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
@TableName("github_sync_targets")
@Accessors(chain = true)
public class GithubSyncTarget {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField("repository_owner")
    private String repositoryOwner;
    @TableField("repository_name")
    private String repositoryName;
    @TableField("branch_name")
    private String branchName;
    @TableField("file_path")
    private String filePath;
    private Boolean enabled;
    @TableField("created_by")
    private Long createdBy;
    @TableField("updated_by")
    private Long updatedBy;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("last_attempt_at")
    private OffsetDateTime lastAttemptAt;
    @TableField("last_success_at")
    private OffsetDateTime lastSuccessAt;
    @TableField("last_error")
    private String lastError;
    @TableField("last_commit_sha")
    private String lastCommitSha;
}
