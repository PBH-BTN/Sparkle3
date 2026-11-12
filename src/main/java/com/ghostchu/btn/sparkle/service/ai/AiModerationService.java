package com.ghostchu.btn.sparkle.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.entity.AiModerationJob;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AiModerationJobMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentRevisionMapper;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import com.ghostchu.btn.sparkle.service.ai.OpenAiModerationClient.Verdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiModerationService {
    private final AiModerationJobMapper jobMapper;
    private final IpQueryCommentMapper commentMapper;
    private final IpQueryCommentRevisionMapper revisionMapper;
    private final UserMapper userMapper;
    private final AiModerationSettingsService settingsService;
    private final OpenAiModerationClient moderationClient;
    private final UserMessageService messageService;

    public boolean enabled() {
        return settingsService.get().enabled();
    }

    public int maxAttempts() {
        return settingsService.get().maxAttempts();
    }

    @Transactional
    public boolean enqueueComment(IpQueryComment comment) {
        if (!enabled()) {
            return false;
        }
        AiModerationSettings settings = settingsService.get();
        jobMapper.insert(new AiModerationJob()
                .setContentType("COMMENT")
                .setContentRefId(comment.getId())
                .setContentVersion(comment.getReviewGeneration())
                .setSubjectUserId(comment.getAuthorUserId())
                .setTextContent(comment.getContent())
                .setStatus("PENDING")
                .setAttemptCount(0)
                .setMaxAttempts(settings.maxAttempts())
                .setProviderModel(settings.model())
                .setNextAttemptAt(OffsetDateTime.now())
                .setCreatedAt(OffsetDateTime.now())
                .setUpdatedAt(OffsetDateTime.now()));
        return true;
    }

    @Transactional
    public void approveImmediately(IpQueryComment comment) {
        OffsetDateTime now = OffsetDateTime.now();
        commentMapper.update(null, new LambdaUpdateWrapper<IpQueryComment>()
                .set(IpQueryComment::getModerationStatus, "APPROVED")
                .set(IpQueryComment::getModerationReason, null)
                .set(IpQueryComment::getUpdatedAt, now)
                .eq(IpQueryComment::getId, comment.getId())
                .eq(IpQueryComment::getReviewGeneration, comment.getReviewGeneration())
                .eq(IpQueryComment::getModerationStatus, "PENDING"));
        revisionMapper.update(null,
                new LambdaUpdateWrapper<IpQueryCommentRevision>()
                        .set(IpQueryCommentRevision::getModerationStatus, "APPROVED")
                        .set(IpQueryCommentRevision::getModerationReason, null)
                        .eq(IpQueryCommentRevision::getCommentId, comment.getId())
                        .eq(IpQueryCommentRevision::getRevision, comment.getRevision()));
        comment.setModerationStatus("APPROVED");
        comment.setModerationReason(null);
        notifyReplyIfNeeded(comment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueUsername(
            long userId,
            long generation,
            String nickname
    ) {
        AiModerationSettings settings = settingsService.get();
        if (!settings.enabled()) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getNicknameModerationStatus, "APPROVED")
                    .set(User::getNicknameModerationReason, null)
                    .eq(User::getId, userId)
                    .eq(User::getNicknameReviewGeneration, generation));
            return;
        }
        jobMapper.insert(new AiModerationJob()
                .setContentType("USERNAME")
                .setContentRefId(userId)
                .setContentVersion(generation)
                .setSubjectUserId(userId)
                .setTextContent(nickname)
                .setStatus("PENDING")
                .setAttemptCount(0)
                .setMaxAttempts(settings.maxAttempts())
                .setProviderModel(settings.model())
                .setNextAttemptAt(OffsetDateTime.now())
                .setCreatedAt(OffsetDateTime.now())
                .setUpdatedAt(OffsetDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueAvatar(
            long userId,
            long generation,
            String avatarUrl,
            String nickname
    ) {
        AiModerationSettings settings = settingsService.get();
        if (!settings.enabled() || !settings.moderateAvatars()) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getAvatarModerationStatus, "APPROVED")
                    .set(User::getAvatarModerationReason, null)
                    .eq(User::getId, userId)
                    .eq(User::getAvatarReviewGeneration, generation));
            return;
        }
        jobMapper.insert(new AiModerationJob()
                .setContentType("AVATAR")
                .setContentRefId(userId)
                .setContentVersion(generation)
                .setSubjectUserId(userId)
                .setTextContent(nickname == null ? "" : nickname)
                .setImageUrl(avatarUrl)
                .setStatus("PENDING")
                .setAttemptCount(0)
                .setMaxAttempts(settings.maxAttempts())
                .setProviderModel(settings.model())
                .setNextAttemptAt(OffsetDateTime.now())
                .setCreatedAt(OffsetDateTime.now())
                .setUpdatedAt(OffsetDateTime.now()));
    }

    @Transactional
    public Optional<AiModerationJob> claimNext() {
        OffsetDateTime now = OffsetDateTime.now();
        jobMapper.recoverStaleJobs(now.minusMinutes(10));
        AiModerationJob job = jobMapper.selectNextForUpdate();
        if (job == null) {
            return Optional.empty();
        }
        String worker = "sparkle-" + UUID.randomUUID();
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<AiModerationJob>()
                .set(AiModerationJob::getStatus, "RUNNING")
                .set(AiModerationJob::getLockedBy, worker)
                .set(AiModerationJob::getLockedAt, now)
                .set(AiModerationJob::getStartedAt, now)
                .set(AiModerationJob::getAttemptCount, safeAttempt(job) + 1)
                .set(AiModerationJob::getUpdatedAt, now)
                .eq(AiModerationJob::getId, job.getId())
                .eq(AiModerationJob::getStatus, "PENDING"));
        if (updated == 0) {
            return Optional.empty();
        }
        job.setStatus("RUNNING")
                .setLockedBy(worker)
                .setLockedAt(now)
                .setStartedAt(now)
                .setAttemptCount(safeAttempt(job) + 1);
        return Optional.of(job);
    }

    public Verdict review(AiModerationJob job) throws BusinessException {
        AiModerationSettings settings = settingsService.get();
        if (!settings.enabled()) {
            return new Verdict(
                    true,
                    "AI moderation has been disabled",
                    List.of(),
                    1,
                    "{}"
            );
        }
        return moderationClient.review(
                settings,
                job.getContentType(),
                context(job),
                job.getTextContent(),
                "AVATAR".equals(job.getContentType()) ? job.getImageUrl() : null
        );
    }

    @Transactional
    public void complete(AiModerationJob job, Verdict verdict) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean updated = applyVerdict(job, verdict, now);
        if (!updated) {
            jobMapper.update(null, new LambdaUpdateWrapper<AiModerationJob>()
                    .set(AiModerationJob::getStatus,
                            verdict.approved() ? "APPROVED" : "REJECTED")
                    .set(AiModerationJob::getResultJson, verdict.rawJson())
                    .set(AiModerationJob::getRejectionReason, verdict.reason())
                    .set(AiModerationJob::getCompletedAt, now)
                    .set(AiModerationJob::getLockedBy, null)
                    .set(AiModerationJob::getLockedAt, null)
                    .set(AiModerationJob::getUpdatedAt, now)
                    .eq(AiModerationJob::getId, job.getId())
                    .eq(AiModerationJob::getStatus, "RUNNING"));
            return;
        }
        jobMapper.update(null, new LambdaUpdateWrapper<AiModerationJob>()
                .set(AiModerationJob::getStatus,
                        verdict.approved() ? "APPROVED" : "REJECTED")
                .set(AiModerationJob::getResultJson, verdict.rawJson())
                .set(AiModerationJob::getRejectionReason, verdict.reason())
                .set(AiModerationJob::getCompletedAt, now)
                .set(AiModerationJob::getLockedBy, null)
                .set(AiModerationJob::getLockedAt, null)
                .set(AiModerationJob::getUpdatedAt, now)
                .eq(AiModerationJob::getId, job.getId())
                .eq(AiModerationJob::getStatus, "RUNNING"));
    }

    @Transactional
    public void fail(AiModerationJob job, Throwable failure) {
        OffsetDateTime now = OffsetDateTime.now();
        int attempt = safeAttempt(job);
        boolean finalFailure = attempt >= safeMaxAttempts(job);
        String error = failure == null || failure.getMessage() == null
                ? "Unknown AI moderation error"
                : limit(failure.getMessage(), 2_000);
        jobMapper.update(null, new LambdaUpdateWrapper<AiModerationJob>()
                .set(AiModerationJob::getStatus,
                        finalFailure ? "FAILED" : "PENDING")
                .set(AiModerationJob::getLastError, error)
                .set(AiModerationJob::getNextAttemptAt,
                        finalFailure
                                ? now
                                : now.plusSeconds(retryDelaySeconds(attempt)))
                .set(AiModerationJob::getCompletedAt,
                        finalFailure ? now : null)
                .set(AiModerationJob::getLockedBy, null)
                .set(AiModerationJob::getLockedAt, null)
                .set(AiModerationJob::getUpdatedAt, now)
                .eq(AiModerationJob::getId, job.getId())
                .eq(AiModerationJob::getStatus, "RUNNING"));
        if (finalFailure) {
            applyFailure(job, error, now);
        }
        log.warn("AI moderation job {} failed on attempt {}: {}",
                job.getId(), attempt, error);
    }

    @Transactional
    public int requeueAllComments() throws BusinessException {
        if (!enabled()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Enable AI moderation before starting a full review"
            );
        }
        return jobMapper.requeueAllComments(maxAttempts());
    }

    @Transactional
    public int retryFailed(String contentType) {
        return jobMapper.retryFailedJobs(
                contentType == null || contentType.isBlank()
                        ? null
                        : contentType.trim().toUpperCase()
        );
    }

    public ModerationMetrics metrics() {
        return new ModerationMetrics(
                count("PENDING"),
                count("RUNNING"),
                count("APPROVED"),
                count("REJECTED"),
                count("FAILED"),
                lastFailure()
        );
    }

    public List<AiModerationJob> recentJobs(int limit) {
        return jobMapper.selectList(new QueryWrapper<AiModerationJob>()
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, Math.min(100, limit))));
    }

    private boolean applyVerdict(
            AiModerationJob job,
            Verdict verdict,
            OffsetDateTime now
    ) {
        return switch (job.getContentType()) {
            case "COMMENT" -> applyCommentVerdict(job, verdict, now);
            case "USERNAME" -> applyUsernameVerdict(job, verdict, now);
            case "AVATAR" -> applyAvatarVerdict(job, verdict, now);
            default -> false;
        };
    }

    private boolean applyCommentVerdict(
            AiModerationJob job,
            Verdict verdict,
            OffsetDateTime now
    ) {
        int updated = commentMapper.update(null,
                new LambdaUpdateWrapper<IpQueryComment>()
                        .set(IpQueryComment::getModerationStatus,
                                verdict.approved() ? "APPROVED" : "REJECTED")
                        .set(IpQueryComment::getModerationReason,
                                verdict.approved() ? null : reason(verdict))
                        .set(IpQueryComment::getUpdatedAt, now)
                        .eq(IpQueryComment::getId, job.getContentRefId())
                        .eq(IpQueryComment::getReviewGeneration,
                                job.getContentVersion())
                        .in(IpQueryComment::getModerationStatus,
                                "PENDING", "FAILED")
        );
        if (updated == 0) {
            return false;
        }
        IpQueryComment comment = commentMapper.selectById(job.getContentRefId());
        if (comment == null) {
            return false;
        }
        revisionMapper.update(null,
                new LambdaUpdateWrapper<IpQueryCommentRevision>()
                        .set(IpQueryCommentRevision::getModerationStatus,
                                verdict.approved() ? "APPROVED" : "REJECTED")
                        .set(IpQueryCommentRevision::getModerationReason,
                                verdict.approved() ? null : reason(verdict))
                        .eq(IpQueryCommentRevision::getCommentId, comment.getId())
                        .eq(IpQueryCommentRevision::getRevision, comment.getRevision())
        );
        if (verdict.approved()) {
            notifyReplyIfNeeded(comment);
        } else {
            notifyRejectedComment(comment, verdict);
        }
        return true;
    }

    private boolean applyUsernameVerdict(
            AiModerationJob job,
            Verdict verdict,
            OffsetDateTime now
    ) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getNicknameModerationStatus,
                        verdict.approved() ? "APPROVED" : "REJECTED")
                .set(User::getNicknameModerationReason,
                        verdict.approved() ? null : reason(verdict))
                .eq(User::getId, job.getContentRefId())
                .eq(User::getNicknameReviewGeneration, job.getContentVersion())
                .in(User::getNicknameModerationStatus, "PENDING", "FAILED"));
        if (updated > 0 && !verdict.approved()) {
            notifyRejectedProfile(
                    job.getSubjectUserId(),
                    "用户名",
                    reason(verdict)
            );
        }
        return updated > 0;
    }

    private boolean applyAvatarVerdict(
            AiModerationJob job,
            Verdict verdict,
            OffsetDateTime now
    ) {
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getAvatarModerationStatus,
                        verdict.approved() ? "APPROVED" : "REJECTED")
                .set(User::getAvatarModerationReason,
                        verdict.approved() ? null : reason(verdict))
                .eq(User::getId, job.getContentRefId())
                .eq(User::getAvatarReviewGeneration, job.getContentVersion())
                .in(User::getAvatarModerationStatus, "PENDING", "FAILED"));
        if (updated > 0 && !verdict.approved()) {
            notifyRejectedProfile(
                    job.getSubjectUserId(),
                    "头像",
                    reason(verdict)
            );
        }
        return updated > 0;
    }

    private void applyFailure(
            AiModerationJob job,
            String error,
            OffsetDateTime now
    ) {
        String reason = "自动审核服务暂时不可用，内容将继续对其他人隐藏。管理员可稍后重新审核。";
        switch (job.getContentType()) {
            case "COMMENT" -> {
                int updated = commentMapper.update(null,
                        new LambdaUpdateWrapper<IpQueryComment>()
                                .set(IpQueryComment::getModerationStatus, "FAILED")
                                .set(IpQueryComment::getModerationReason, reason)
                                .set(IpQueryComment::getUpdatedAt, now)
                                .eq(IpQueryComment::getId, job.getContentRefId())
                                .eq(IpQueryComment::getReviewGeneration,
                                        job.getContentVersion())
                                .eq(IpQueryComment::getModerationStatus, "PENDING"));
                if (updated > 0) {
                    IpQueryComment comment =
                            commentMapper.selectById(job.getContentRefId());
                    if (comment != null) {
                        revisionMapper.update(null,
                                new LambdaUpdateWrapper<IpQueryCommentRevision>()
                                        .set(IpQueryCommentRevision::getModerationStatus,
                                                "FAILED")
                                        .set(IpQueryCommentRevision::getModerationReason,
                                                reason)
                                        .eq(IpQueryCommentRevision::getCommentId,
                                                comment.getId())
                                        .eq(IpQueryCommentRevision::getRevision,
                                                comment.getRevision()));
                    }
                }
            }
            case "USERNAME" -> userMapper.update(null,
                    new LambdaUpdateWrapper<User>()
                            .set(User::getNicknameModerationStatus, "FAILED")
                            .set(User::getNicknameModerationReason, reason)
                            .eq(User::getId, job.getContentRefId())
                            .eq(User::getNicknameReviewGeneration,
                                    job.getContentVersion())
                            .eq(User::getNicknameModerationStatus, "PENDING"));
            case "AVATAR" -> userMapper.update(null,
                    new LambdaUpdateWrapper<User>()
                            .set(User::getAvatarModerationStatus, "FAILED")
                            .set(User::getAvatarModerationReason, reason)
                            .eq(User::getId, job.getContentRefId())
                            .eq(User::getAvatarReviewGeneration,
                                    job.getContentVersion())
                            .eq(User::getAvatarModerationStatus, "PENDING"));
            default -> {
            }
        }
    }

    private void notifyReplyIfNeeded(IpQueryComment comment) {
        if (comment.getParentCommentId() == null
                || comment.getReplyNotifiedAt() != null) {
            return;
        }
        IpQueryComment parent = commentMapper.selectById(
                comment.getParentCommentId()
        );
        if (parent == null
                || parent.getDeletedAt() != null
                || parent.getAuthorUserId() == null
                || parent.getAuthorUserId().equals(comment.getAuthorUserId())) {
            markReplyNotified(comment.getId());
            return;
        }
        try {
            messageService.publishSystemToUser(
                    parent.getAuthorUserId(),
                    NotificationCategory.SOCIAL_ALERT,
                    "有人回复了你的 IP 评论",
                    """
                            你的评论收到了新的回复。

                            评论网段：%s
                            回复摘要：%s

                            前往 IP 查询页面查看完整讨论。
                            """.formatted(
                            comment.getNetwork(),
                            excerpt(comment.getContent(), 240)
                    ),
                    false
            );
            markReplyNotified(comment.getId());
        } catch (Exception exception) {
            log.warn("Unable to send reply notification for comment {}: {}",
                    comment.getId(), exception.getMessage());
        }
    }

    private void notifyRejectedComment(IpQueryComment comment, Verdict verdict) {
        if (comment.getAuthorUserId() == null) {
            return;
        }
        try {
            messageService.publishSystemToUser(
                    comment.getAuthorUserId(),
                    NotificationCategory.GENERAL_NOTICE,
                    "IP 评论区内容审核未通过",
                    """
                            你的 IP/CIDR 评论未通过自动内容审核，已对其他人隐藏。

                            评论网段：%s
                            审核原因：%s

                            你可以修改评论后重新提交审核，也可以自行删除该评论。
                            """.formatted(comment.getNetwork(), reason(verdict)),
                    false
            );
        } catch (Exception exception) {
            log.warn("Unable to send moderation notice for comment {}: {}",
                    comment.getId(), exception.getMessage());
        }
    }

    private void notifyRejectedProfile(
            Long userId,
            String field,
            String reason
    ) {
        if (userId == null) {
            return;
        }
        try {
            messageService.publishSystemToUser(
                    userId,
                    NotificationCategory.GENERAL_NOTICE,
                    field + "自动审核未通过",
                    """
                            %s未通过自动内容审核，已对其他人隐藏。

                            审核原因：%s

                            再次登录并更新资料后，会重新提交审核。
                            """.formatted(field, reason),
                    false
            );
        } catch (Exception exception) {
            log.warn("Unable to send moderation notice for user {}: {}",
                    userId, exception.getMessage());
        }
    }

    private void markReplyNotified(long commentId) {
        commentMapper.update(null,
                new LambdaUpdateWrapper<IpQueryComment>()
                        .set(IpQueryComment::getReplyNotifiedAt,
                                OffsetDateTime.now())
                        .eq(IpQueryComment::getId, commentId)
                        .isNull(IpQueryComment::getReplyNotifiedAt));
    }

    private String context(AiModerationJob job) {
        if ("COMMENT".equals(job.getContentType())) {
            IpQueryComment comment = commentMapper.selectById(job.getContentRefId());
            return comment == null
                    ? ""
                    : "发布者正在讨论 IP/CIDR 网段 " + comment.getNetwork();
        }
        if ("AVATAR".equals(job.getContentType())) {
            return "用户头像，用户名：" + (job.getTextContent() == null
                    ? ""
                    : job.getTextContent());
        }
        return "Sparkle BTN 用户显示名称";
    }

    private String reason(Verdict verdict) {
        return verdict.reason() == null || verdict.reason().isBlank()
                ? "内容不符合社区审核标准"
                : verdict.reason();
    }

    private String excerpt(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "…";
    }

    private long count(String status) {
        return jobMapper.selectCount(new QueryWrapper<AiModerationJob>()
                .eq("status", status));
    }

    private AiModerationJob lastFailure() {
        return jobMapper.selectOne(new QueryWrapper<AiModerationJob>()
                .eq("status", "FAILED")
                .orderByDesc("updated_at")
                .last("LIMIT 1"));
    }

    private int safeAttempt(AiModerationJob job) {
        return job.getAttemptCount() == null ? 0 : job.getAttemptCount();
    }

    private int safeMaxAttempts(AiModerationJob job) {
        return job.getMaxAttempts() == null || job.getMaxAttempts() < 1
                ? 5
                : job.getMaxAttempts();
    }

    private long retryDelaySeconds(int attempt) {
        return Math.min(600, 15L << Math.min(5, Math.max(0, attempt)));
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    public record ModerationMetrics(
            long pending,
            long running,
            long approved,
            long rejected,
            long failed,
            AiModerationJob lastFailure
    ) {
    }
}
