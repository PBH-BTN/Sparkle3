package com.ghostchu.btn.sparkle.service.ipquery;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentCreateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentDetail;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentRevisionView;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentRow;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentUpdateRequest;
import com.ghostchu.btn.sparkle.entity.AiModerationJob;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AiModerationJobMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentRevisionMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpQueryCommentAdminService {
    private static final int MAX_USER_AGENT_LENGTH = 1_000;
    private static final int MAX_REASON_LENGTH = 1_500;
    private static final Set<String> MODERATION_STATUSES =
            Set.of("PENDING", "APPROVED", "REJECTED", "FAILED");
    private static final Set<String> EDITABLE_STATUSES =
            Set.of("PENDING", "APPROVED", "REJECTED");

    private final IpQueryCommentMapper commentMapper;
    private final IpQueryCommentRevisionMapper revisionMapper;
    private final AiModerationJobMapper jobMapper;
    private final IUserService userService;
    private final IpQuerySettingsService settingsService;
    private final AiModerationService moderationService;

    public PageResult<AdminCommentRow> list(
            int requestedPage,
            int requestedSize,
            String keyword,
            String scope,
            String status,
            boolean includeDeleted
    ) throws BusinessException {
        int page = Math.max(1, requestedPage);
        int size = Math.min(100, Math.max(1, requestedSize));
        String term = keyword == null ? "" : keyword.trim();
        QueryWrapper<IpQueryComment> wrapper = new QueryWrapper<>();
        if (!includeDeleted) {
            wrapper.isNull("deleted_at");
        }
        if (!term.isBlank()) {
            Long commentId = parseId(term);
            wrapper.and(query -> {
                query.like("content", term);
                if (commentId != null) {
                    query.or().eq("id", commentId);
                }
            });
        }
        String normalizedScope = normalizeScope(scope);
        if (normalizedScope != null) {
            wrapper.apply("network <<= CAST({0} AS cidr)", normalizedScope);
        }
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null) {
            wrapper.eq("moderation_status", normalizedStatus);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        Page<IpQueryComment> result = commentMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );
        List<IpQueryComment> comments = result.getRecords();
        Map<Long, User> authors = loadUsers(comments.stream()
                .map(IpQueryComment::getAuthorUserId)
                .toList());
        Map<Long, String> jobStatuses = latestJobStatuses(comments.stream()
                .map(IpQueryComment::getId)
                .toList());
        List<AdminCommentRow> records = comments.stream()
                .map(comment -> row(
                        comment,
                        authors.get(comment.getAuthorUserId()),
                        jobStatuses.get(comment.getId())
                ))
                .toList();
        long total = result.getTotal();
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(records, page, size, total, pages);
    }

    public AdminCommentDetail detail(long commentId) throws BusinessException {
        IpQueryComment comment = requireComment(commentId);
        List<IpQueryCommentRevision> revisions = revisionMapper.selectList(
                new QueryWrapper<IpQueryCommentRevision>()
                        .eq("comment_id", commentId)
                        .orderByAsc("revision")
        );
        List<Long> userIds = new ArrayList<>();
        userIds.add(comment.getAuthorUserId());
        revisions.forEach(revision -> userIds.add(revision.getEditorUserId()));
        Map<Long, User> users = loadUsers(userIds);
        Map<Long, String> jobStatuses =
                latestJobStatuses(List.of(commentId));
        List<AdminCommentRevisionView> history = revisions.stream()
                .map(revision -> revisionView(
                        revision,
                        users.get(revision.getEditorUserId())
                ))
                .toList();
        return new AdminCommentDetail(
                row(
                        comment,
                        users.get(comment.getAuthorUserId()),
                        jobStatuses.get(commentId)
                ),
                history
        );
    }

    @Transactional
    public AdminCommentDetail create(
            long operatorId,
            AdminCommentCreateRequest request,
            String remoteIp,
            String userAgent
    ) throws BusinessException {
        if (request == null) {
            throw badRequest("Comment data is required");
        }
        String network = normalizeScope(request.network());
        if (network == null) {
            throw badRequest("A network scope is required");
        }
        String content = normalizeContent(
                request.content(),
                commentMaxLength()
        );
        Long authorUserId = parseUserId(request.authorUserId());
        if (authorUserId != null && userService.getById(authorUserId) == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "The selected author does not exist"
            );
        }
        String status = normalizeEditableStatus(request.moderationStatus());
        if (status == null) {
            status = "APPROVED";
        }
        requireModerationAvailable(status);
        String reason = moderationReason(status, request.moderationReason());
        OffsetDateTime now = OffsetDateTime.now();
        IpQueryComment comment = new IpQueryComment()
                .setNetwork(network)
                .setAuthorUserId(authorUserId)
                .setContent(content)
                .setModerationStatus(status)
                .setModerationReason(reason)
                .setReviewGeneration(0L)
                .setRevision(1)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        commentMapper.insert(comment);
        revisionMapper.insert(new IpQueryCommentRevision()
                .setCommentId(comment.getId())
                .setRevision(1)
                .setContent(content)
                .setModerationStatus(status)
                .setModerationReason(reason)
                .setEditorUserId(operatorId)
                .setEditorIp(parseIp(remoteIp))
                .setEditorUserAgent(normalizeUserAgent(userAgent))
                .setCreatedAt(now));
        if ("PENDING".equals(status)) {
            moderationService.enqueueComment(comment);
        }
        log.info("Admin {} created IP query comment {} on {}",
                operatorId, comment.getId(), network);
        return detail(comment.getId());
    }

    @Transactional
    public AdminCommentDetail update(
            long operatorId,
            long commentId,
            AdminCommentUpdateRequest request,
            String remoteIp,
            String userAgent
    ) throws BusinessException {
        if (request == null) {
            throw badRequest("Comment data is required");
        }
        IpQueryComment comment = requireComment(commentId);
        if (comment.getDeletedAt() != null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Restore the comment before editing it"
            );
        }
        if (request.revision() != null
                && !Objects.equals(request.revision(), comment.getRevision())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "This comment changed elsewhere. Reload it before editing"
            );
        }
        String content = null;
        boolean contentChanged = false;
        if (request.content() != null) {
            content = normalizeContent(request.content(), commentMaxLength());
            contentChanged = !content.equals(comment.getContent());
        }
        String requestedStatus = normalizeEditableStatus(
                request.moderationStatus()
        );
        boolean statusChanged = requestedStatus != null
                && !requestedStatus.equals(comment.getModerationStatus());
        String status = requestedStatus == null
                ? contentChanged ? "APPROVED" : comment.getModerationStatus()
                : requestedStatus;
        requireModerationAvailable(status);
        boolean changed = contentChanged || statusChanged;
        String reason = statusChanged || contentChanged
                ? moderationReason(status, request.moderationReason())
                : comment.getModerationReason();
        int revision = value(comment.getRevision())
                + (contentChanged ? 1 : 0);
        long generation = value(comment.getReviewGeneration())
                + (changed ? 1 : 0);
        OffsetDateTime now = OffsetDateTime.now();
        LambdaUpdateWrapper<IpQueryComment> update =
                new LambdaUpdateWrapper<IpQueryComment>()
                        .set(IpQueryComment::getModerationStatus, status)
                        .set(IpQueryComment::getModerationReason, reason)
                        .set(IpQueryComment::getReviewGeneration, generation)
                        .set(IpQueryComment::getUpdatedAt, now)
                        .eq(IpQueryComment::getId, commentId)
                        .eq(IpQueryComment::getRevision,
                                value(comment.getRevision()));
        if (contentChanged) {
            update.set(IpQueryComment::getContent, content)
                    .set(IpQueryComment::getRevision, revision);
        }
        if (commentMapper.update(null, update) == 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "This comment changed elsewhere. Reload it before editing"
            );
        }
        if (contentChanged) {
            revisionMapper.insert(new IpQueryCommentRevision()
                    .setCommentId(commentId)
                    .setRevision(revision)
                    .setContent(content)
                    .setModerationStatus(status)
                    .setModerationReason(reason)
                    .setEditorUserId(operatorId)
                    .setEditorIp(parseIp(remoteIp))
                    .setEditorUserAgent(normalizeUserAgent(userAgent))
                    .setCreatedAt(now));
        } else {
            revisionMapper.update(null,
                    new LambdaUpdateWrapper<IpQueryCommentRevision>()
                            .set(IpQueryCommentRevision::getModerationStatus,
                                    status)
                            .set(IpQueryCommentRevision::getModerationReason,
                                    reason)
                            .eq(IpQueryCommentRevision::getCommentId, commentId)
                            .eq(IpQueryCommentRevision::getRevision,
                                    value(comment.getRevision())));
        }
        if (changed && "PENDING".equals(status)) {
            comment.setContent(content == null ? comment.getContent() : content)
                    .setRevision(revision)
                    .setReviewGeneration(generation)
                    .setModerationStatus(status)
                    .setModerationReason(reason);
            moderationService.enqueueComment(comment);
        }
        log.info("Admin {} updated IP query comment {} (status {}, revision {})",
                operatorId, commentId, status, revision);
        return detail(commentId);
    }

    @Transactional
    public AdminCommentDetail delete(long operatorId, long commentId)
            throws BusinessException {
        IpQueryComment comment = requireComment(commentId);
        if (comment.getDeletedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            commentMapper.update(null,
                    new LambdaUpdateWrapper<IpQueryComment>()
                            .set(IpQueryComment::getDeletedAt, now)
                            .set(IpQueryComment::getUpdatedAt, now)
                            .eq(IpQueryComment::getId, commentId)
                            .isNull(IpQueryComment::getDeletedAt));
            log.info("Admin {} hid IP query comment {}", operatorId, commentId);
        }
        return detail(commentId);
    }

    @Transactional
    public AdminCommentDetail restore(long operatorId, long commentId)
            throws BusinessException {
        IpQueryComment comment = requireComment(commentId);
        if (comment.getDeletedAt() != null) {
            commentMapper.update(null,
                    new LambdaUpdateWrapper<IpQueryComment>()
                            .set(IpQueryComment::getDeletedAt, null)
                            .set(IpQueryComment::getUpdatedAt,
                                    OffsetDateTime.now())
                            .eq(IpQueryComment::getId, commentId)
                            .isNotNull(IpQueryComment::getDeletedAt));
            log.info("Admin {} restored IP query comment {}",
                    operatorId, commentId);
        }
        return detail(commentId);
    }

    @Transactional
    public AdminCommentDetail reaudit(long operatorId, long commentId)
            throws BusinessException {
        IpQueryComment comment = requireComment(commentId);
        if (comment.getDeletedAt() != null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Restore the comment before re-reviewing it"
            );
        }
        if (!moderationService.enabled()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Enable AI moderation before re-reviewing comments"
            );
        }
        long generation = value(comment.getReviewGeneration()) + 1;
        OffsetDateTime now = OffsetDateTime.now();
        commentMapper.update(null, new LambdaUpdateWrapper<IpQueryComment>()
                .set(IpQueryComment::getModerationStatus, "PENDING")
                .set(IpQueryComment::getModerationReason, null)
                .set(IpQueryComment::getReviewGeneration, generation)
                .set(IpQueryComment::getUpdatedAt, now)
                .eq(IpQueryComment::getId, commentId));
        revisionMapper.update(null,
                new LambdaUpdateWrapper<IpQueryCommentRevision>()
                        .set(IpQueryCommentRevision::getModerationStatus,
                                "PENDING")
                        .set(IpQueryCommentRevision::getModerationReason, null)
                        .eq(IpQueryCommentRevision::getCommentId, commentId)
                        .eq(IpQueryCommentRevision::getRevision,
                                value(comment.getRevision())));
        comment.setModerationStatus("PENDING")
                .setModerationReason(null)
                .setReviewGeneration(generation);
        moderationService.enqueueComment(comment);
        log.info("Admin {} queued IP query comment {} for AI re-review",
                operatorId, commentId);
        return detail(commentId);
    }

    private IpQueryComment requireComment(long commentId)
            throws BusinessException {
        IpQueryComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        return comment;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> users = new HashMap<>();
        userService.listByIds(ids).forEach(user -> users.put(user.getId(), user));
        return users;
    }

    private Map<Long, String> latestJobStatuses(List<Long> commentIds) {
        List<Long> ids = commentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> statuses = new HashMap<>();
        jobMapper.selectList(new QueryWrapper<AiModerationJob>()
                        .select("content_ref_id", "status")
                        .eq("content_type", "COMMENT")
                        .in("content_ref_id", ids)
                        .orderByDesc("id"))
                .forEach(job -> statuses.putIfAbsent(
                        job.getContentRefId(),
                        job.getStatus()
                ));
        return statuses;
    }

    private AdminCommentRow row(
            IpQueryComment comment,
            User author,
            String latestJobStatus
    ) {
        return new AdminCommentRow(
                comment.getId(),
                comment.getNetwork(),
                comment.getParentCommentId(),
                stringId(comment.getAuthorUserId()),
                author == null ? null : author.getNickname(),
                author == null ? null : author.getAvatarUrl(),
                comment.getContent(),
                value(comment.getRevision()),
                comment.getModerationStatus(),
                comment.getModerationReason(),
                value(comment.getReviewGeneration()),
                ipText(comment.getAuthorIp()),
                comment.getAuthorUserAgent(),
                comment.getAnonymizedAt() != null,
                comment.getDeletedAt() != null,
                latestJobStatus,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getDeletedAt()
        );
    }

    private AdminCommentRevisionView revisionView(
            IpQueryCommentRevision revision,
            User editor
    ) {
        return new AdminCommentRevisionView(
                value(revision.getRevision()),
                revision.getContent(),
                revision.getModerationStatus(),
                revision.getModerationReason(),
                stringId(revision.getEditorUserId()),
                editor == null ? null : editor.getNickname(),
                ipText(revision.getEditorIp()),
                revision.getEditorUserAgent(),
                revision.getCreatedAt()
        );
    }

    String normalizeScope(String raw) throws BusinessException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.length() > 128 || value.contains("://")) {
            throw badRequest("A valid IP address or CIDR is required");
        }
        IPAddressString parsed = new IPAddressString(value);
        if (!parsed.isValid()) {
            throw badRequest("A valid IP address or CIDR is required");
        }
        IPAddress address;
        try {
            address = parsed.toAddress();
        } catch (AddressStringException exception) {
            throw badRequest("A valid IP address or CIDR is required");
        }
        if (address.isIPv4Convertible() && !address.isIPv4()) {
            address = address.toIPv4();
        }
        Integer prefix = address.getNetworkPrefixLength();
        if (prefix == null) {
            return address.withoutPrefixLength().toCanonicalString();
        }
        return address.toPrefixBlock(prefix)
                .getLower()
                .withoutPrefixLength()
                .toCanonicalString()
                + "/" + prefix;
    }

    private String normalizeContent(String value, long maxLength)
            throws BusinessException {
        if (value == null) {
            throw badRequest("Comment content is required");
        }
        String normalized = value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]+", "")
                .trim();
        if (normalized.isBlank()) {
            throw badRequest("Comment content is required");
        }
        if (normalized.length() > maxLength) {
            throw badRequest("Comment content exceeds the configured length limit");
        }
        return normalized;
    }

    private long commentMaxLength() {
        return Math.max(100L, settingsService.get().maxLength());
    }

    private String normalizeStatus(String value) throws BusinessException {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!MODERATION_STATUSES.contains(normalized)) {
            throw badRequest("Unknown comment moderation status");
        }
        return normalized;
    }

    private String normalizeEditableStatus(String value)
            throws BusinessException {
        String normalized = normalizeStatus(value);
        if (normalized != null && !EDITABLE_STATUSES.contains(normalized)) {
            throw badRequest(
                    "Comments can only be pending, approved or rejected"
            );
        }
        return normalized;
    }

    private void requireModerationAvailable(String status)
            throws BusinessException {
        if ("PENDING".equals(status) && !moderationService.enabled()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Enable AI moderation before sending comments to review"
            );
        }
    }

    private String moderationReason(String status, String requested) {
        if (!"REJECTED".equals(status)) {
            return null;
        }
        String reason = requested == null ? "" : requested.trim();
        if (reason.isEmpty()) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_REASON_LENGTH);
    }

    private Long parseUserId(String raw) throws BusinessException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 19) {
            throw badRequest("A valid user id is required");
        }
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw badRequest("A valid user id is required");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw badRequest("A valid user id is required");
        }
    }

    private Long parseId(String value) {
        if (value == null || value.isEmpty() || value.length() > 18) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return null;
            }
        }
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeUserAgent(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").trim();
        if (normalized.length() > MAX_USER_AGENT_LENGTH) {
            normalized = normalized.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private InetAddress parseIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.split(",")[0].trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return InetAddress.ofLiteral(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String ipText(InetAddress address) {
        return address == null ? null : address.getHostAddress();
    }

    private String stringId(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
