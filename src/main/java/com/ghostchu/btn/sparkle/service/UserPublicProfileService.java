package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PrivacySettingsView;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PublicUserProfileResponse;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility;
import com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.util.UnitConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserPublicProfileService {
    private final IUserService userService;
    private final UserLifetimeTrafficMapper lifetimeTrafficMapper;
    private final UserPrivacyService privacyService;

    public PublicUserProfileResponse profile(long currentUserId, long targetUserId)
            throws BusinessException {
        User sender = requireUser(currentUserId);
        User target = requireUser(targetUserId);
        UserPrivacySettings privacy = privacySettings(target);
        boolean self = sender.getId().equals(target.getId());

        String nickname = null;
        if (self || (target.isNicknamePubliclyVisible()
                && privacy.allowsFullUsername())) {
            nickname = target.getNickname();
        } else if (target.isNicknamePubliclyVisible()
                && privacy.allowsBlurredUsername()) {
            nickname = blurNickname(target.getNickname());
        }

        String avatarUrl = null;
        if (self || (target.isAvatarPubliclyVisible()
                && !privacy.hidesAvatar())) {
            avatarUrl = target.getAvatarUrl();
        }

        Long scoreBytes = null;
        if (self || privacy.showsPoints()) {
            scoreBytes = target.getScoreBytes() == null ? 0L : target.getScoreBytes();
        }

        return new PublicUserProfileResponse(
                String.valueOf(target.getId()),
                nickname,
                avatarUrl,
                PrivacySettingsView.from(privacy),
                scoreBytes == null ? null : UnitConverter.autoUnit(scoreBytes),
                scoreBytes == null ? null : Long.toString(scoreBytes),
                nickname == null,
                target.getBannedAt() != null,
                self,
                canSendMessage(sender, target),
                canContactEmail(sender, target),
                self || privacy.lifetimeTrafficVisibility() == LifetimeTrafficVisibility.SHOW
                        ? lifetimeTrafficMapper.selectByUserId(targetUserId) : null
        );
    }

    public boolean canSendMessage(User sender, User target) {
        if (sender == null || target == null || sender.getId().equals(target.getId())) {
            return false;
        }
        return sender.getBannedAt() == null
                && target.getBannedAt() == null
                && privacySettings(target).receiveUserMessages();
    }

    public boolean canContactEmail(User sender, User target) {
        if (sender == null || target == null || sender.getId().equals(target.getId())) {
            return false;
        }
        if (sender.getBannedAt() != null || target.getBannedAt() != null) {
            return false;
        }
        if (!privacySettings(target).receiveUserMessages()) {
            return false;
        }
        String email = target.getEmail();
        return email != null
                && !email.isBlank()
                && !email.toLowerCase(Locale.ROOT).endsWith("@sparkle.system");
    }

    public UserPrivacySettings privacySettings(User user) {
        if (user == null || user.getId() == null) {
            return UserPrivacySettings.defaults(0);
        }
        return privacyService.get(user.getId());
    }

    public User requireUser(long userId) throws BusinessException {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    public String blurNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "***";
        }
        if (nickname.length() <= 4) {
            return "*".repeat(nickname.length());
        }
        return nickname.substring(0, 3)
                + "*".repeat(nickname.length() - 4)
                + nickname.substring(nickname.length() - 1);
    }
}
