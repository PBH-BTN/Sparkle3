package com.ghostchu.btn.sparkle.service.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserAccountDeletionRequest;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.entity.UserContactEmailDelivery;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.entity.UserPrivacySettingsEntity;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision;
import com.ghostchu.btn.sparkle.entity.AiModerationJob;
import com.ghostchu.btn.sparkle.mapper.AiModerationJobMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentRevisionMapper;
import com.ghostchu.btn.sparkle.mapper.UserAccountDeletionRequestMapper;
import com.ghostchu.btn.sparkle.mapper.UserContactEmailDeliveryMapper;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.mapper.UserPrivacySettingsMapper;
import com.ghostchu.btn.sparkle.mapper.UserRelMapper;
import com.ghostchu.btn.sparkle.mapper.UserappMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionFinalizer {
    private final UserAccountDeletionRequestMapper requestMapper;
    private final UserMapper userMapper;
    private final UserappMapper userappMapper;
    private final UserRelMapper userRelMapper;
    private final UserContactEmailDeliveryMapper deliveryMapper;
    private final UserMessageMapper messageMapper;
    private final UserPrivacySettingsMapper privacyMapper;
    private final IpQueryCommentMapper ipQueryCommentMapper;
    private final IpQueryCommentRevisionMapper ipQueryCommentRevisionMapper;
    private final AiModerationJobMapper aiModerationJobMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountDeletionPendingRegistry pendingRegistry;

    @Transactional
    public boolean finalizeDeletion(long userId) {
        UserAccountDeletionRequest requestEntity = requestMapper.selectForUpdate(userId);
        if (requestEntity == null) {
            afterCommit(() -> pendingRegistry.clear(userId));
            return false;
        }
        RequestRow request = new RequestRow(
                requestEntity.getStatus(),
                requestEntity.getRequestedAt(),
                requestEntity.getCoolingEndsAt()
        );
        OffsetDateTime now = OffsetDateTime.now();
        if (!"PENDING".equals(request.status())
                || request.coolingEndsAt().isAfter(now)) {
            if (!"PENDING".equals(request.status())) {
                afterCommit(() -> pendingRegistry.clear(userId));
            }
            return false;
        }

        User userEntity = userMapper.selectForUpdate(userId);
        if (userEntity == null) {
            requestMapper.update(null,
                    new LambdaUpdateWrapper<UserAccountDeletionRequest>()
                            .set(UserAccountDeletionRequest::getStatus, "CANCELLED")
                            .set(UserAccountDeletionRequest::getCancelledAt, now)
                            .set(UserAccountDeletionRequest::getCancellationReason,
                                    "USER_NOT_FOUND")
                            .set(UserAccountDeletionRequest::getUpdatedAt, now)
                            .eq(UserAccountDeletionRequest::getUserId, userId)
                            .eq(UserAccountDeletionRequest::getStatus, "PENDING")
            );
            afterCommit(() -> pendingRegistry.clear(userId));
            return false;
        }

        DeletedUser user = new DeletedUser(
                userEntity.getId(),
                userEntity.getEmail(),
                userEntity.getNickname()
        );
        String anonymousNickname = userId + "-userdeleted";
        String anonymousEmail = userId + "@userdeleted.pbh-btn.com";
        userappMapper.selectList(
                new QueryWrapper<Userapp>().eq("owner", userId)
        ).forEach(application -> {
            if (application.getBannedAt() == null) application.setBannedAt(now);
            application.setBannedReason(UserAccountDeletionService.DELETED_APP_REASON)
                    .setComment(UserAccountDeletionService.DELETED_APP_REASON);
            userappMapper.updateById(application);
        });
        userRelMapper.selectList(
                new QueryWrapper<UserRel>()
                        .eq("bind_user_id", userId)
                        .eq("platform", "github")
        ).forEach(relation -> {
            relation.setPlatformUserId("0")
                    .setPlatformUserLogin(anonymousNickname)
                    .setPlatformUserEmail(anonymousEmail);
            userRelMapper.updateById(relation);
        });
        deliveryMapper.selectList(
                new QueryWrapper<UserContactEmailDelivery>()
                        .and(query -> query.eq("sender_user_id", userId)
                                .or().eq("recipient_user_id", userId))
        ).forEach(delivery -> {
            if (delivery.getSenderUserId() == userId) {
                delivery.setSenderNickname(anonymousNickname)
                        .setSenderEmail(anonymousEmail)
                        .setSenderIp(null)
                        .setSenderUserAgent(null);
            }
            if (delivery.getRecipientUserId() == userId) {
                delivery.setRecipientNickname(anonymousNickname)
                        .setRecipientEmail(anonymousEmail);
            }
            deliveryMapper.updateById(delivery);
        });
        messageMapper.selectList(
                new QueryWrapper<UserMessage>().eq("sender_user_id", userId)
        ).forEach(message -> {
            message.setSenderDisplayName(anonymousNickname)
                    .setSenderIp(null)
                    .setSenderUserAgent(null);
            messageMapper.updateById(message);
        });
        ipQueryCommentMapper.update(null,
                new LambdaUpdateWrapper<IpQueryComment>()
                        .set(IpQueryComment::getAuthorIp, null)
                        .set(IpQueryComment::getAuthorUserAgent, null)
                        .set(IpQueryComment::getAnonymizedAt, now)
                        .eq(IpQueryComment::getAuthorUserId, userId));
        ipQueryCommentRevisionMapper.update(null,
                new LambdaUpdateWrapper<IpQueryCommentRevision>()
                        .set(IpQueryCommentRevision::getEditorIp, null)
                        .set(IpQueryCommentRevision::getEditorUserAgent, null)
                        .eq(IpQueryCommentRevision::getEditorUserId, userId));
        aiModerationJobMapper.update(null,
                new LambdaUpdateWrapper<AiModerationJob>()
                        .set(AiModerationJob::getTextContent, null)
                        .set(AiModerationJob::getImageUrl, null)
                        .in(AiModerationJob::getContentType, "USERNAME", "AVATAR")
                        .eq(AiModerationJob::getSubjectUserId, userId));
        userEntity.setEmail(anonymousEmail)
                .setNickname(anonymousNickname)
                .setAvatar("/img/anonymous-user.png")
                .setNicknameModerationStatus("APPROVED")
                .setAvatarModerationStatus("APPROVED")
                .setNicknameModerationReason(null)
                .setAvatarModerationReason(null)
                .setPrivacyLevel(UserPrivacyLevel.HIGH)
                .setRole("user")
                .setBannedAt(userEntity.getBannedAt() == null
                        ? now : userEntity.getBannedAt())
                .setBannedReason(UserAccountDeletionService.DELETED_APP_REASON);
        userMapper.updateById(userEntity);
        UserPrivacySettingsEntity privacy = privacyMapper.selectById(userId);
        if (privacy == null) {
            privacyMapper.insert(new UserPrivacySettingsEntity()
                    .setUserId(userId)
                    .setAvatarVisibility("HIDDEN")
                    .setUsernameVisibility("BLURRED")
                    .setPointsVisibility("HIDDEN")
                    .setReceiveUserMessages(false)
                    .setUpdatedAt(now));
        } else {
            privacy.setAvatarVisibility("HIDDEN")
                    .setUsernameVisibility("BLURRED")
                    .setPointsVisibility("HIDDEN")
                    .setReceiveUserMessages(false)
                    .setUpdatedAt(now);
            privacyMapper.updateById(privacy);
        }
        requestMapper.update(null,
                new LambdaUpdateWrapper<UserAccountDeletionRequest>()
                        .set(UserAccountDeletionRequest::getStatus, "COMPLETED")
                        .set(UserAccountDeletionRequest::getCompletedAt, now)
                        .set(UserAccountDeletionRequest::getLastError, null)
                        .set(UserAccountDeletionRequest::getUpdatedAt, now)
                        .eq(UserAccountDeletionRequest::getUserId, userId)
                        .eq(UserAccountDeletionRequest::getStatus, "PENDING")
        );

        eventPublisher.publishEvent(new AccountDeletionNotificationEvent(
                userId,
                user.email(),
                user.nickname(),
                AccountDeletionNotificationEvent.Kind.COMPLETED,
                request.requestedAt(),
                request.coolingEndsAt(),
                now,
                null
        ));
        afterCommit(() -> pendingRegistry.clear(userId));
        log.info("Completed account deletion and anonymization for user {}", userId);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelAfterFinalizationFailure(long userId, Throwable failure) {
        OffsetDateTime now = OffsetDateTime.now();
        UserAccountDeletionRequest requestEntity = requestMapper.selectForUpdate(userId);
        if (requestEntity == null) {
            return;
        }
        RequestRow request = new RequestRow(
                requestEntity.getStatus(),
                requestEntity.getRequestedAt(),
                requestEntity.getCoolingEndsAt()
        );
        if (!"PENDING".equals(request.status())) {
            return;
        }
        String message = failure == null || failure.getMessage() == null
                ? "Unknown finalization error"
                : failure.getMessage();
        int updated = requestMapper.update(null,
                new LambdaUpdateWrapper<UserAccountDeletionRequest>()
                        .set(UserAccountDeletionRequest::getStatus, "CANCELLED")
                        .set(UserAccountDeletionRequest::getCancelledAt, now)
                        .set(UserAccountDeletionRequest::getCancellationReason,
                                "FINALIZATION_FAILED")
                        .set(UserAccountDeletionRequest::getLastError,
                                limit(message, 2_000))
                        .set(UserAccountDeletionRequest::getUpdatedAt, now)
                        .eq(UserAccountDeletionRequest::getUserId, userId)
                        .eq(UserAccountDeletionRequest::getStatus, "PENDING")
        );
        if (updated == 0) {
            return;
        }
        User userEntity = userMapper.selectById(userId);
        if (userEntity == null) {
            return;
        }
        DeletedUser user = new DeletedUser(
                userEntity.getId(),
                userEntity.getEmail(),
                userEntity.getNickname()
        );
        eventPublisher.publishEvent(new AccountDeletionNotificationEvent(
                userId,
                user.email(),
                user.nickname(),
                AccountDeletionNotificationEvent.Kind.CANCELLED,
                request.requestedAt(),
                request.coolingEndsAt(),
                null,
                "系统在处理注销时发生错误，本次申请已取消；账号资料未被匿名化，请稍后重新申请"
        ));
        afterCommit(() -> pendingRegistry.clear(userId));
    }

    private record RequestRow(
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime coolingEndsAt
    ) {
    }

    private record DeletedUser(long id, String email, String nickname) {
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }
}
