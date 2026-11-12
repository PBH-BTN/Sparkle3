package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;

public record UserPrivacySettings(
        long userId,
        AvatarVisibility avatarVisibility,
        UsernameVisibility usernameVisibility,
        PointsVisibility pointsVisibility,
        boolean receiveUserMessages
) {
    public static UserPrivacySettings defaults(long userId) {
        return new UserPrivacySettings(
                userId,
                AvatarVisibility.BLURRED,
                UsernameVisibility.BLURRED,
                PointsVisibility.SHOW,
                true
        );
    }

    public boolean allowsOriginalAvatar() {
        return avatarVisibility == AvatarVisibility.ORIGINAL;
    }

    public boolean allowsBlurredAvatar() {
        return avatarVisibility == AvatarVisibility.BLURRED;
    }

    public boolean hidesAvatar() {
        return avatarVisibility == AvatarVisibility.HIDDEN;
    }

    public boolean allowsFullUsername() {
        return usernameVisibility == UsernameVisibility.FULL;
    }

    public boolean allowsBlurredUsername() {
        return usernameVisibility == UsernameVisibility.BLURRED;
    }

    public boolean showsPoints() {
        return pointsVisibility == PointsVisibility.SHOW;
    }
}
