package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class CommunityGroupModels {
    private CommunityGroupModels() {
    }

    public record GithubTeamInput(
            String slug,
            String name
    ) {
    }

    public record GithubTeamView(
            String slug,
            String name
    ) {
    }

    public record GroupInput(
            String slug,
            String type,
            String titleZh,
            String titleEn,
            String descriptionZh,
            String descriptionEn,
            String url,
            String githubOrganization,
            boolean enabled,
            int sortOrder,
            List<GithubTeamInput> teams
    ) {
    }

    public record GroupView(
            long id,
            String slug,
            String type,
            String titleZh,
            String titleEn,
            String descriptionZh,
            String descriptionEn,
            String url,
            String githubOrganization,
            boolean enabled,
            int sortOrder,
            List<GithubTeamView> teams,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long updatedBy
    ) {
    }

    public record SettingsUpdateRequest(
            String personalAccessToken,
            Boolean clearToken,
            String apiBaseUrl
    ) {
    }

    public record SettingsView(
            boolean tokenConfigured,
            String apiBaseUrl,
            List<GroupView> groups
    ) {
    }

    public record TeamValidationRequest(
            String organization,
            List<String> teamSlugs
    ) {
    }

    public record TeamValidationView(
            long id,
            String slug,
            String name
    ) {
    }

    public record TeamValidationResult(
            String organization,
            List<TeamValidationView> teams
    ) {
    }
}
