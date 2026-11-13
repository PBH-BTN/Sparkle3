package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class CommunityGroupPublicModels {
    private CommunityGroupPublicModels() {
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
            List<TeamView> teams,
            boolean githubLinked,
            String githubLogin,
            boolean githubConfigured
    ) {
    }

    public record TeamView(
            String slug,
            String name
    ) {
    }

    public record TeamStatusView(
            String slug,
            String name,
            String state
    ) {
    }

    public record GithubStatusView(
            long groupId,
            String status,
            String organizationState,
            List<TeamStatusView> teams,
            String message,
            OffsetDateTime checkedAt
    ) {
    }

    public record GithubJoinResult(
            String status,
            String message
    ) {
    }
}
