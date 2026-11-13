package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class IpQueryCommentAdminModels {
    private IpQueryCommentAdminModels() {
    }

    public record AdminCommentRow(
            long id,
            String network,
            Long parentCommentId,
            String authorUserId,
            String authorNickname,
            String authorAvatarUrl,
            String content,
            int revision,
            String moderationStatus,
            String moderationReason,
            long reviewGeneration,
            String authorIp,
            String authorUserAgent,
            boolean anonymized,
            boolean deleted,
            String latestJobStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt
    ) {
    }

    public record AdminCommentRevisionView(
            int revision,
            String content,
            String moderationStatus,
            String moderationReason,
            String editorUserId,
            String editorNickname,
            String editorIp,
            String editorUserAgent,
            OffsetDateTime createdAt
    ) {
    }

    public record AdminCommentDetail(
            AdminCommentRow comment,
            List<AdminCommentRevisionView> revisions
    ) {
    }

    public record AdminCommentCreateRequest(
            String network,
            String content,
            String authorUserId,
            String moderationStatus,
            String moderationReason
    ) {
    }

    public record AdminCommentUpdateRequest(
            Integer revision,
            String content,
            String moderationStatus,
            String moderationReason
    ) {
    }
}
