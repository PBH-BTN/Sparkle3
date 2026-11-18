package com.ghostchu.btn.sparkle.controller.ui.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ghostchu.btn.sparkle.service.impl.QueryIpServiceImpl.IpQueryResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class IpQueryModels {
    private IpQueryModels() {
    }

    public record LookupResponse(
            String input,
            String normalizedAddress,
            String canonicalCidr,
            String networkAddress,
            String rangeStart,
            String rangeEnd,
            int ipVersion,
            int prefixLength,
            String totalAddresses,
            String expandedAddress,
            String binaryAddress,
            String decimalAddress,
            String reverseDnsName,
            List<String> flags,
            boolean globallyRoutable,
            String defaultCommentScope,
            int defaultCommentPrefix,
            long commentsCount,
            List<CommentScopeOption> commentScopes,
            GeoView geoStart,
            GeoView geoEnd,
            IpQueryResult activity,
            CommentAccess commentAccess,
            OffsetDateTime generatedAt
    ) {
    }

    public record GeoView(
            String country,
            String countryIso,
            String province,
            String city,
            String district,
            String asn,
            String asnOrganization,
            String asnNetwork,
            String isp,
            String networkType
    ) {
    }

    public record CommentAccess(
            boolean visible,
            boolean publishingEnabled,
            boolean moderationEnabled,
            int maxLength,
            int hourlyLimit
    ) {
    }

    public record CommentScopeOption(
            int prefix,
            String scope,
            boolean selected
    ) {
    }

    public record CommentPage(
            List<CommentView> records,
            long current,
            long size,
            long total,
            long pages,
            String scope,
            CommentAccess access,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<Long, CommentQuote> replyQuotes
    ) {
        public CommentPage(List<CommentView> records, long current, long size, long total, long pages,
                           String scope, CommentAccess access) {
            this(records, current, size, total, pages, scope, access, Map.of());
        }
    }

    public record CommentQuote(
            String authorNickname,
            boolean authorModerationHidden,
            String excerpt,
            OffsetDateTime createdAt
    ) {
    }

    public record CommentView(
            long id,
            Long parentId,
            String network,
            String authorId,
            String authorNickname,
            String authorAvatarUrl,
            boolean authorAnonymous,
            boolean authorModerationHidden,
            String content,
            int revision,
            boolean edited,
            String moderationStatus,
            String moderationReason,
            String authorIp,
            String authorUserAgent,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            boolean self,
            boolean canEdit,
            boolean canDelete,
            List<CommentRevisionView> history
    ) {
        public CommentView withAuthorAvatarUrl(String avatarUrl) {
            return new CommentView(id, parentId, network, authorId, authorNickname, avatarUrl, authorAnonymous,
                    authorModerationHidden, content, revision, edited, moderationStatus, moderationReason, authorIp,
                    authorUserAgent, createdAt, updatedAt, self, canEdit, canDelete, history);
        }
    }

    public record CommentRevisionView(
            int revision,
            String content,
            String moderationStatus,
            String moderationReason,
            String editorIp,
            String editorUserAgent,
            OffsetDateTime createdAt
    ) {
    }

    public record CreateCommentRequest(
            String scope,
            Long parentId,
            String content
    ) {
    }

    public record UpdateCommentRequest(
            String content,
            Integer revision
    ) {
    }
}
