package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PublicUserProfileResponse;
import com.ghostchu.btn.sparkle.entity.User;
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

    public PublicUserProfileResponse profile(long currentUserId, long targetUserId)
            throws BusinessException {
        User sender = requireUser(currentUserId);
        User target = requireUser(targetUserId);
        UserPrivacyLevel privacy = privacyLevel(target);
        boolean self = sender.getId().equals(target.getId());

        String nickname = null;
        if (self || privacy.isAllowFullUserName()) {
            nickname = target.getNickname();
        } else if (privacy.isAllowBlurredUsername()) {
            nickname = blurNickname(target.getNickname());
        }

        String avatarUrl = null;
        if (self || privacy.isAllowOriginalAvatar() || privacy.isAllowBlurredAvatar()) {
            avatarUrl = target.getAvatarUrl();
        }

        Long scoreBytes = null;
        if (self || privacy.isAllowPublicPoints()) {
            scoreBytes = target.getScoreBytes() == null ? 0L : target.getScoreBytes();
        }

        return new PublicUserProfileResponse(
                String.valueOf(target.getId()),
                nickname,
                avatarUrl,
                privacy.name(),
                scoreBytes == null ? null : UnitConverter.autoUnit(scoreBytes),
                scoreBytes == null ? null : Long.toString(scoreBytes),
                nickname == null,
                target.getBannedAt() != null,
                self,
                canContactEmail(sender, target)
        );
    }

    public boolean canContactEmail(User sender, User target) {
        if (sender == null || target == null || sender.getId().equals(target.getId())) {
            return false;
        }
        if (sender.getBannedAt() != null || target.getBannedAt() != null) {
            return false;
        }
        UserPrivacyLevel privacy = privacyLevel(target);
        if (!privacy.isAllowContactEmail()) {
            return false;
        }
        String email = target.getEmail();
        return email != null
                && !email.isBlank()
                && !email.toLowerCase(Locale.ROOT).endsWith("@sparkle.system");
    }

    public UserPrivacyLevel privacyLevel(User user) {
        return user.getPrivacyLevel() == null ? UserPrivacyLevel.MEDIUM : user.getPrivacyLevel();
    }

    public User requireUser(long userId) throws BusinessException {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private String blurNickname(String nickname) {
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
