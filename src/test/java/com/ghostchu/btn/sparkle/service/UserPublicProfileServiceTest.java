package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPublicProfileServiceTest {
    @Test
    void mediumPrivacyIncludesPointBalance() throws Exception {
        IUserService userService = mock(IUserService.class);
        User viewer = user(1L, UserPrivacyLevel.LOW, 0L);
        User target = user(2L, UserPrivacyLevel.MEDIUM, 4_096L);
        when(userService.getById(1L)).thenReturn(viewer);
        when(userService.getById(2L)).thenReturn(target);

        var profile = new UserPublicProfileService(userService).profile(1L, 2L);

        assertEquals("4096", profile.scoreBytesRaw());
        assertNotNull(profile.scoreBytesDisplay());
    }

    @Test
    void highPrivacyHidesOtherUsersPointBalance() throws Exception {
        IUserService userService = mock(IUserService.class);
        User viewer = user(1L, UserPrivacyLevel.LOW, 0L);
        User target = user(2L, UserPrivacyLevel.HIGH, 4_096L);
        when(userService.getById(1L)).thenReturn(viewer);
        when(userService.getById(2L)).thenReturn(target);

        var profile = new UserPublicProfileService(userService).profile(1L, 2L);

        assertNull(profile.scoreBytesRaw());
        assertNull(profile.scoreBytesDisplay());
    }

    @Test
    void highPrivacyStillShowsOwnPointBalance() throws Exception {
        IUserService userService = mock(IUserService.class);
        User target = user(3L, UserPrivacyLevel.HIGH, 4_096L);
        when(userService.getById(3L)).thenReturn(target);

        var profile = new UserPublicProfileService(userService).profile(3L, 3L);

        assertEquals("4096", profile.scoreBytesRaw());
    }

    private User user(long id, UserPrivacyLevel privacyLevel, long scoreBytes) {
        return new User()
                .setId(id)
                .setNickname("Sparkle User")
                .setPrivacyLevel(privacyLevel)
                .setScoreBytes(scoreBytes);
    }
}
