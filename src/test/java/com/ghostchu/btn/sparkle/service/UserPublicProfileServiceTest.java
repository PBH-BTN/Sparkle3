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

        var profile = new UserPublicProfileService(userService,
                mock(com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper.class), privacyService).profile(1L, 2L);

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

        var profile = new UserPublicProfileService(userService,
                mock(com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper.class), privacyService).profile(1L, 2L);

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
                false,
                com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility.HIDDEN
        ));

        var profile = new UserPublicProfileService(userService,
                mock(com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper.class), privacyService).profile(1L, 2L);

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

        var profile = new UserPublicProfileService(userService,
                mock(com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper.class), privacyService).profile(3L, 3L);

        assertEquals("4096", profile.scoreBytesRaw());
    }

    @Test
    void lifetimeTrafficIsNeverReadForOtherUsersWhenHidden() throws Exception {
        var users = mock(IUserService.class);
        var privacy = mock(UserPrivacyService.class);
        var traffic = mock(com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper.class);
        when(users.getById(1L)).thenReturn(user(1L, 0L));
        when(users.getById(2L)).thenReturn(user(2L, 0L));
        when(privacy.get(2L)).thenReturn(settings(2L, PointsVisibility.SHOW));

        var profile = new UserPublicProfileService(users, traffic, privacy).profile(1L, 2L);

        assertNull(profile.lifetimeTraffic());
        org.mockito.Mockito.verifyNoInteractions(traffic);
    }

    @Test
    void lifetimeTrafficIsVisibleToOwnerAndWhenExplicitlyShared() throws Exception {
        var users = mock(IUserService.class);
        var privacy = mock(UserPrivacyService.class);
        var traffic = mock(com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper.class);
        var totals = new com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.LifetimeTraffic(
                "9007199254740993", "0", "42", "0", null, true, null);
        when(users.getById(1L)).thenReturn(user(1L, 0L));
        when(users.getById(2L)).thenReturn(user(2L, 0L));
        when(traffic.selectByUserId(2L)).thenReturn(totals);
        when(privacy.get(2L)).thenReturn(settings(2L, PointsVisibility.HIDDEN));
        var service = new UserPublicProfileService(users, traffic, privacy);
        assertEquals(totals, service.profile(2L, 2L).lifetimeTraffic());

        when(privacy.get(2L)).thenReturn(new UserPrivacySettings(2L,
                AvatarVisibility.BLURRED, UsernameVisibility.BLURRED, PointsVisibility.HIDDEN,
                true, com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility.SHOW));
        assertEquals(totals, service.profile(1L, 2L).lifetimeTraffic());
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
                true,
                com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility.HIDDEN
        );
    }
}
