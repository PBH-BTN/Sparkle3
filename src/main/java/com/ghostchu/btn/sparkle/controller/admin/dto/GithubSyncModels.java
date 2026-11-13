package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class GithubSyncModels {
    private GithubSyncModels() {
    }

    public record SourceView(
            String id,
            String kind,
            String defaultFileName,
            boolean available
    ) {
    }

    public record TargetView(
            long id,
            String name,
            String repositoryOwner,
            String repositoryName,
            String branchName,
            String filePath,
            boolean enabled,
            List<String> sourceIds,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime lastAttemptAt,
            OffsetDateTime lastSuccessAt,
            String lastError,
            String lastCommitSha
    ) {
    }

    public record TargetInput(
            String name,
            String repositoryOwner,
            String repositoryName,
            String branchName,
            String filePath,
            boolean enabled,
            List<String> sourceIds
    ) {
    }

    public record SettingsView(
            String schedule,
            boolean tokenConfigured,
            List<SourceView> sources,
            List<TargetView> targets
    ) {
    }

    public record UpdateSettingsRequest(
            String schedule,
            String personalAccessToken,
            Boolean clearToken
    ) {
    }

    public record TargetRunResult(
            long targetId,
            String targetName,
            boolean success,
            String message,
            String commitSha
    ) {
    }

    public record RunResult(
            int total,
            int succeeded,
            int failed,
            List<TargetRunResult> results
    ) {
    }
}
