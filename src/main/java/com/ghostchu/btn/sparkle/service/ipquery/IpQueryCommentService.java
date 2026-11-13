package com.ghostchu.btn.sparkle.service.ipquery;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentAccess;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentRevisionView;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentView;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CreateCommentRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.UpdateCommentRequest;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentRevisionMapper;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.UserPrivacySettings;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class IpQueryCommentService {
    private static final int MAX_USER_AGENT_LENGTH = 1_000;
    private static final int MAX_HISTORY_ITEMS = 100;

    private final IpQueryCommentMapper commentMapper;
    private final IpQueryCommentRevisionMapper revisionMapper;
    private final IUserService userService;
    private final UserPrivacyService privacyService;
    private final IpQuerySettingsService settingsService;
    private final IpQueryLookupService lookupService;
    private final AiModerationService moderationService;

    public CommentPage list(
            long viewerUserId,
            String requestedScope,
            int requestedPage,
            int requestedSize
    ) throws BusinessException {
        CommentAccess access = lookupService.access();
        String scope = commentScope(requestedScope);
        int page = Math.max(1, requestedPage);
        int size = Math.min(50, Math.max(1, requestedSize));
        if (!access.visible()) {
            return new CommentPage(
                    List.of(),
                    page,
                    size,
                    0,
                    0,
                    scope,
                    access
            );
        }
        long total = commentMapper.countVisible(scope, viewerUserId);
        List<IpQueryComment> comments = total == 0
                ? List.of()
                : commentMapper.selectVisiblePage(
                        scope,
                        viewerUserId,
                        size,
                        (long) (page - 1) * size
                );
        List<CommentView> records = mapComments(
                comments,
                viewerUserId,
                canViewCommentMetadata(viewerUserId)
        );
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new CommentPage(
                records,
                page,
                size,
                total,
                pages,
                scope,
                access
        );
    }

    @Transactional
    public CommentView create(
            long userId,
            CreateCommentRequest request,
            String remoteIp,
            String userAgent
    ) throws BusinessException {
        IpQuerySettingsService.Settings settings = settingsService.get();
        if (!settings.commentsVisible() || !settings.commentsEnabled()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "IP query comments are currently closed"
            );
        }
        if (request == null) {
            throw badRequest("Comment data is required");
        }
        String content = normalizeContent(request.content(), settings.maxLength());
        String network = commentScope(request.scope());
        IpQueryComment parent = null;
        if (request.parentId() != null) {
            parent = commentMapper.selectById(request.parentId());
            if (parent == null
                    || parent.getDeletedAt() != null
                    || !"APPROVED".equals(parent.getModerationStatus())) {
                throw new BusinessException(HttpStatus.NOT_FOUND,
                        "The comment being replied to no longer exists");
            }
            network = parent.getNetwork();
        }
        long recent = commentMapper.selectCount(
                new QueryWrapper<IpQueryComment>()
                        .eq("author_user_id", userId)
                        .ge("created_at", OffsetDateTime.now().minusHours(1))
                        .isNull("deleted_at")
        );
        if (recent >= settings.hourlyLimit()) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Comment hourly limit reached"
            );
        }
        IpQueryComment comment = new IpQueryComment()
                .setNetwork(network)
                .setParentCommentId(parent == null ? null : parent.getId())
                .setAuthorUserId(userId)
                .setContent(content)
                .setModerationStatus("PENDING")
                .setReviewGeneration(0L)
                .setRevision(1)
                .setAuthorIp(parseIp(remoteIp))
                .setAuthorUserAgent(normalizeUserAgent(userAgent))
                .setCreatedAt(OffsetDateTime.now())
                .setUpdatedAt(OffsetDateTime.now());
        commentMapper.insert(comment);
        revisionMapper.insert(new IpQueryCommentRevision()
                .setCommentId(comment.getId())
                .setRevision(1)
                .setContent(content)
                .setModerationStatus("PENDING")
                .setEditorUserId(userId)
                .setEditorIp(comment.getAuthorIp())
                .setEditorUserAgent(comment.getAuthorUserAgent())
                .setCreatedAt(comment.getCreatedAt()));
        if (!moderationService.enqueueComment(comment)) {
            moderationService.approveImmediately(comment);
        }
        return mapComment(commentMapper.selectById(comment.getId()), userId);
    }

    @Transactional
    public CommentView update(
            long userId,
            long commentId,
            UpdateCommentRequest request,
            String remoteIp,
            String userAgent
    ) throws BusinessException {
        IpQueryComment comment = requireOwnedComment(userId, commentId);
        if (request == null) {
            throw badRequest("Comment data is required");
        }
        if (request.revision() != null
                && !Objects.equals(request.revision(), comment.getRevision())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "This comment changed elsewhere. Reload it before editing"
            );
        }
        IpQuerySettingsService.Settings settings = settingsService.get();
        if (!settings.commentsVisible() || !settings.commentsEnabled()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "IP query comments are currently closed"
            );
        }
        String content = normalizeContent(request.content(), settings.maxLength());
        int nextRevision = comment.getRevision() + 1;
        long nextGeneration = value(comment.getReviewGeneration()) + 1;
        InetAddress ip = parseIp(remoteIp);
        String normalizedUserAgent = normalizeUserAgent(userAgent);
        int updated = commentMapper.update(null,
                new LambdaUpdateWrapper<IpQueryComment>()
                        .set(IpQueryComment::getContent, content)
                        .set(IpQueryComment::getModerationStatus, "PENDING")
                        .set(IpQueryComment::getModerationReason, null)
                        .set(IpQueryComment::getReviewGeneration, nextGeneration)
                        .set(IpQueryComment::getRevision, nextRevision)
                        .set(IpQueryComment::getAuthorIp, ip)
                        .set(IpQueryComment::getAuthorUserAgent, normalizedUserAgent)
                        .set(IpQueryComment::getUpdatedAt, OffsetDateTime.now())
                        .eq(IpQueryComment::getId, commentId)
                        .eq(IpQueryComment::getAuthorUserId, userId)
                        .eq(IpQueryComment::getRevision, comment.getRevision())
                        .isNull(IpQueryComment::getDeletedAt));
        if (updated == 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "This comment changed elsewhere. Reload it before editing"
            );
        }
        revisionMapper.insert(new IpQueryCommentRevision()
                .setCommentId(commentId)
                .setRevision(nextRevision)
                .setContent(content)
                .setModerationStatus("PENDING")
                .setEditorUserId(userId)
                .setEditorIp(ip)
                .setEditorUserAgent(normalizedUserAgent)
                .setCreatedAt(OffsetDateTime.now()));
        IpQueryComment updatedComment = commentMapper.selectById(commentId);
        if (!moderationService.enqueueComment(updatedComment)) {
            moderationService.approveImmediately(updatedComment);
        }
        return mapComment(commentMapper.selectById(commentId), userId);
    }

    @Transactional
    public void delete(long userId, long commentId) throws BusinessException {
        requireOwnedComment(userId, commentId);
        int updated = commentMapper.update(null,
                new LambdaUpdateWrapper<IpQueryComment>()
                        .set(IpQueryComment::getDeletedAt, OffsetDateTime.now())
                        .eq(IpQueryComment::getId, commentId)
                        .eq(IpQueryComment::getAuthorUserId, userId)
                        .isNull(IpQueryComment::getDeletedAt));
        if (updated == 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Comment could not be deleted");
        }
    }

    public int requeueAll() throws BusinessException {
        return moderationService.requeueAllComments();
    }

    public boolean isAdmin(long userId) {
        User user = userService.getById(userId);
        return AdminPolicy.isAdmin(user);
    }

    /**
     * Posting IP and user agent stay visible to administrators and moderators
     * only; regular users and the comment author never receive them.
     */
    private boolean canViewCommentMetadata(long userId) {
        if (userId <= 0) {
            return false;
        }
        return AdminPolicy.hasModeratorAccess(userService.getById(userId));
    }

    private IpQueryComment requireOwnedComment(long userId, long commentId)
            throws BusinessException {
        IpQueryComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        if (!Objects.equals(comment.getAuthorUserId(), userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "You can only modify your own comments");
        }
        return comment;
    }

    private List<CommentView> mapComments(
            List<IpQueryComment> comments,
            long viewerUserId,
            boolean canViewMetadata
    ) {
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = comments.stream()
                .map(IpQueryComment::getAuthorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = new HashMap<>();
        Map<Long, UserPrivacySettings> privacy = new HashMap<>();
        if (!userIds.isEmpty()) {
            userService.listByIds(userIds).forEach(user -> users.put(user.getId(), user));
            privacy.putAll(privacyService.getAll(userIds));
            users.values().forEach(user -> privacy.putIfAbsent(
                    user.getId(),
                    UserPrivacySettings.defaults(user.getId())
            ));
        }
        Map<Long, List<IpQueryCommentRevision>> histories = new LinkedHashMap<>();
        List<Long> commentIds = comments.stream().map(IpQueryComment::getId).toList();
        revisionMapper.selectList(new QueryWrapper<IpQueryCommentRevision>()
                .in("comment_id", commentIds)
                .orderByAsc("revision"))
                .forEach(revision -> histories
                        .computeIfAbsent(revision.getCommentId(), ignored -> new ArrayList<>())
                        .add(revision));
        return comments.stream()
                .map(comment -> mapComment(
                        comment,
                        viewerUserId,
                        users.get(comment.getAuthorUserId()),
                        privacy.get(comment.getAuthorUserId()),
                        histories.getOrDefault(comment.getId(), List.of()),
                        canViewMetadata
                ))
                .toList();
    }

    private CommentView mapComment(IpQueryComment comment, long viewerUserId) {
        User user = comment.getAuthorUserId() == null
                ? null
                : userService.getById(comment.getAuthorUserId());
        UserPrivacySettings privacy = user == null
                ? null
                : privacyService.get(user.getId());
        List<IpQueryCommentRevision> history =
                revisionMapper.selectList(new QueryWrapper<IpQueryCommentRevision>()
                        .eq("comment_id", comment.getId())
                        .orderByAsc("revision"));
        return mapComment(
                comment,
                viewerUserId,
                user,
                privacy,
                history,
                canViewCommentMetadata(viewerUserId)
        );
    }

    private CommentView mapComment(
            IpQueryComment comment,
            long viewerUserId,
            User author,
            UserPrivacySettings privacy,
            List<IpQueryCommentRevision> history,
            boolean canViewMetadata
    ) {
        boolean self = Objects.equals(comment.getAuthorUserId(), viewerUserId);
        boolean authorModerationHidden = author != null
                && !self
                && !author.isNicknamePubliclyVisible();
        String nickname = null;
        String avatarUrl = null;
        if (author != null) {
            if (self) {
                nickname = author.getNickname();
                avatarUrl = author.getAvatarUrl();
            } else if (author.isNicknamePubliclyVisible()
                    && privacy != null
                    && privacy.allowsFullUsername()) {
                nickname = author.getNickname();
            } else if (author.isNicknamePubliclyVisible()
                    && privacy != null
                    && privacy.allowsBlurredUsername()) {
                nickname = blurNickname(author.getNickname());
            }
            if (!self && author.isAvatarPubliclyVisible()
                    && privacy != null
                    && !privacy.hidesAvatar()) {
                avatarUrl = author.getAvatarUrl();
            }
        }
        boolean anonymous = author == null || nickname == null;
        List<CommentRevisionView> visibleHistory = history.stream()
                .filter(item -> self || "APPROVED".equals(item.getModerationStatus()))
                .limit(MAX_HISTORY_ITEMS)
                .map(item -> new CommentRevisionView(
                        item.getRevision(),
                        self || "APPROVED".equals(item.getModerationStatus())
                                ? item.getContent()
                                : "",
                        item.getModerationStatus(),
                        self ? item.getModerationReason() : null,
                        canViewMetadata && !anonymized(comment)
                                ? ipText(item.getEditorIp())
                                : null,
                        canViewMetadata && !anonymized(comment)
                                ? item.getEditorUserAgent()
                                : null,
                        item.getCreatedAt()
                ))
                .toList();
        return new CommentView(
                comment.getId(),
                comment.getParentCommentId(),
                comment.getNetwork(),
                comment.getAuthorUserId() == null
                        ? null
                        : Long.toString(comment.getAuthorUserId()),
                nickname,
                avatarUrl,
                anonymous,
                authorModerationHidden,
                comment.getContent(),
                comment.getRevision(),
                comment.getRevision() > 1,
                comment.getModerationStatus(),
                self ? comment.getModerationReason() : null,
                canViewMetadata && !anonymized(comment)
                        ? ipText(comment.getAuthorIp())
                        : null,
                canViewMetadata && !anonymized(comment)
                        ? comment.getAuthorUserAgent()
                        : null,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                self,
                self && comment.getDeletedAt() == null,
                self && comment.getDeletedAt() == null,
                visibleHistory
        );
    }

    private String commentScope(String raw) throws BusinessException {
        IpScope parsed = IpScope.parse(raw);
        int prefix = parsed.cidrInput()
                ? parsed.prefixLength()
                : parsed.defaultCommentPrefix();
        if (!IpScope.isSupportedCommentPrefix(parsed.version(), prefix)) {
            prefix = parsed.defaultCommentPrefix();
        }
        return parsed.withCommentPrefix(prefix).canonicalCidr();
    }

    private String normalizeContent(String value, int maxLength)
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

    private String blurNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        if (nickname.length() <= 4) {
            return "*".repeat(nickname.length());
        }
        return nickname.substring(0, 3)
                + "*".repeat(nickname.length() - 4)
                + nickname.substring(nickname.length() - 1);
    }

    private boolean anonymized(IpQueryComment comment) {
        return comment.getAnonymizedAt() != null;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
