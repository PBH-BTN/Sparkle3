package com.ghostchu.btn.sparkle.service.moderation;

public record UserProfileChangedEvent(
        long userId,
        long nicknameGeneration,
        long avatarGeneration,
        boolean nicknameChanged,
        boolean avatarChanged,
        String nickname,
        String avatarUrl
) {
}
