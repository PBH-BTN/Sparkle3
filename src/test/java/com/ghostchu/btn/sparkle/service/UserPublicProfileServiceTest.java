package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPublicProfileServiceTest {
    @Test
    void visiblePointsIncludePointBalance() throws Exception {
        IUserService userService = mock(IUserService.class);
        UserPrivacyService privacyService = mock(UserPrivacyService.class);
        User viewer = user(1L, 0L);
        User target = user(2L, 4_096L);
        when(userService.getById(1L)).thenReturn(viewer);
        when(userService.getById(2L)).thenReturn(target);
        when(privacyService.get(1L)).thenReturn(settings(1L, PointsVisibility.SHOW));
        when(privacyService.get(2L)).thenReturn(settings(2L, PointsVisibility.SHOW));

        var profile = new UserPublicProfileService(userService, privacyService).profile(1L, 2L);

        assertEquals("4096", profile.scoreBytesRaw());
        assertNotNull(profile.scoreBytesDisplay());
    }

    @Test
    void hiddenPointsHideOtherUsersPointBalance() throws Exception {
        IUserService userService = mock(IUserService.class);
        UserPrivacyService privacyService = mock(UserPrivacyService.class);
        User viewer = user(1L, 0L);
        User target = user(2L, 4_096L);
        when(userService.getById(1L)).thenReturn(viewer);
        when(userService.getById(2L)).thenReturn(target);
        when(privacyService.get(1L)).thenReturn(settings(1L, PointsVisibility.SHOW));
        when(privacyService.get(2L)).thenReturn(settings(2L, PointsVisibility.HIDDEN));

        var profile = new UserPublicProfileService(userService, privacyService).profile(1L, 2L);

        assertNull(profile.scoreBytesRaw());
        assertNull(profile.scoreBytesDisplay());
    }

    @Test
    void hiddenAvatarAndBlurredUsernameProtectProfile() throws Exception {
        IUserService userService = mock(IUserService.class);
        UserPrivacyService privacyService = mock(UserPrivacyService.class);
        User viewer = user(1L, 0L);
        User target = user(2L, 4_096L);
        when(userService.getById(1L)).thenReturn(viewer);
        when(userService.getById(2L)).thenReturn(target);
        when(privacyService.get(1L)).thenReturn(settings(1L, PointsVisibility.SHOW));
        when(privacyService.get(2L)).thenReturn(new UserPrivacySettings(
                2L,
                AvatarVisibility.HIDDEN,
                UsernameVisibility.BLURRED,
                PointsVisibility.HIDDEN,
                false
        ));

        var profile = new UserPublicProfileService(userService, privacyService).profile(1L, 2L);

        assertNull(profile.scoreBytesRaw());
        assertNull(profile.scoreBytesDisplay());
        assertNull(profile.avatarUrl());
        assertNotNull(profile.nickname());
        org.junit.jupiter.api.Assertions.assertFalse(profile.canSendMessage());
    }

    @Test
    void hiddenPointsStillShowOwnPointBalance() throws Exception {
        IUserService userService = mock(IUserService.class);
        UserPrivacyService privacyService = mock(UserPrivacyService.class);
        User target = user(3L, 4_096L);
        when(userService.getById(3L)).thenReturn(target);
        when(privacyService.get(3L)).thenReturn(settings(3L, PointsVisibility.HIDDEN));

        var profile = new UserPublicProfileService(userService, privacyService).profile(3L, 3L);

        assertEquals("4096", profile.scoreBytesRaw());
    }

    private User user(long id, long scoreBytes) {
        return new User()
                .setId(id)
                .setNickname("Sparkle User")
                .setScoreBytes(scoreBytes);
    }

    private UserPrivacySettings settings(long userId, PointsVisibility points) {
        return new UserPrivacySettings(
                userId,
                AvatarVisibility.BLURRED,
                UsernameVisibility.BLURRED,
                points,
                true
        );
    }
}
