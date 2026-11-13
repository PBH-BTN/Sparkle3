package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.UserDataResponse;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserRelMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlarumIntegrationServiceTest {
    private final UserRelMapper userRelMapper = mock(UserRelMapper.class);
    private final IUserService userService = mock(IUserService.class);
    private final IUserSwarmStatisticsService swarmStatisticsService =
            mock(IUserSwarmStatisticsService.class);
    private final AdminConfigService adminConfigService = mock(AdminConfigService.class);
    private final UserPrivacyService privacyService = mock(UserPrivacyService.class);
    private final UserPublicProfileService profileService =
            new UserPublicProfileService(userService, privacyService);
    private final FlarumIntegrationService service = new FlarumIntegrationService(
            userRelMapper,
            userService,
            swarmStatisticsService,
            adminConfigService,
            profileService
    );

    @Test
    void rejectsInvalidGithubUid() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.findByGithubUid("not-a-number")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(userRelMapper);
    }

    @Test
    void returnsUnboundResponseWhenGithubUidHasNoRelation() throws BusinessException {
        when(userRelMapper.findUserRelByPlatformUserId("github", "12345"))
                .thenReturn(null);

        UserDataResponse response = service.findByGithubUid("12345");

        assertFalse(response.bound());
        assertEquals("12345", response.githubUid());
        assertEquals(null, response.btnUserId());
    }

    @Test
    void returnsBoundUserWithPointsRank() throws BusinessException {
        UserRel relation = new UserRel().setBindUserId(42L);
        User user = new User()
                .setId(42L)
                .setNickname("sparkle-user")
                .setScoreBytes(4096L);

        when(userRelMapper.findUserRelByPlatformUserId("github", "12345"))
                .thenReturn(relation);
        when(userService.getById(42L)).thenReturn(user);
        when(userService.getSystemUids()).thenReturn(List.of());
        when(privacyService.get(42L)).thenReturn(new UserPrivacySettings(
                42L,
                AvatarVisibility.BLURRED,
                UsernameVisibility.FULL,
                PointsVisibility.SHOW,
                true
        ));
        when(adminConfigService.get()).thenReturn(new RuntimeConfigView(
                1,
                10,
                false,
                false,
                false,
                60,
                7,
                "{{title}}",
                "{{content}}"
        ));
        when(userService.count(any())).thenReturn(8L);

        UserDataResponse response = service.findByGithubUid("12345");

        assertTrue(response.bound());
        assertEquals("42", response.btnUserId());
        assertEquals("sparkle-user", response.basicInfo().nickname());
        assertEquals("4096", response.points().balanceBytes());
        assertEquals(9L, response.rankings().points().rank());
        assertTrue(response.privacy().pointsVisible());
        assertFalse(response.traffic7d().enabled());
    }

    @Test
    void hidesPointsWhenUserDisabledThem() throws BusinessException {
        UserRel relation = new UserRel().setBindUserId(42L);
        User user = new User()
                .setId(42L)
                .setNickname("sparkle-user")
                .setScoreBytes(4096L);

        when(userRelMapper.findUserRelByPlatformUserId("github", "12345"))
                .thenReturn(relation);
        when(userService.getById(42L)).thenReturn(user);
        when(userService.getSystemUids()).thenReturn(List.of());
        when(privacyService.get(42L)).thenReturn(new UserPrivacySettings(
                42L,
                AvatarVisibility.BLURRED,
                UsernameVisibility.FULL,
                PointsVisibility.HIDDEN,
                true
        ));
        when(adminConfigService.get()).thenReturn(new RuntimeConfigView(1, 10, false, false, false, 60, 7, "{{title}}", "{{content}}"));

        UserDataResponse response = service.findByGithubUid("12345");

        assertNull(response.points());
        assertNull(response.rankings().points());
        assertFalse(response.privacy().pointsVisible());
    }

    @Test
    void blursOrHidesNicknameAccordingToPrivacy() throws BusinessException {
        UserRel relation = new UserRel().setBindUserId(42L);
        User user = new User().setId(42L).setNickname("sparkle-user");

        when(userRelMapper.findUserRelByPlatformUserId("github", "12345"))
                .thenReturn(relation);
        when(userService.getById(42L)).thenReturn(user);
        when(userService.getSystemUids()).thenReturn(List.of());
        when(privacyService.get(42L)).thenReturn(new UserPrivacySettings(
                42L,
                AvatarVisibility.BLURRED,
                UsernameVisibility.BLURRED,
                PointsVisibility.SHOW,
                true
        ));
        when(adminConfigService.get()).thenReturn(new RuntimeConfigView(1, 10, false, false, false, 60, 7, "{{title}}", "{{content}}"));

        UserDataResponse blurred = service.findByGithubUid("12345");

        assertEquals("spa********r", blurred.basicInfo().nickname());

        user.setNicknameModerationStatus("PENDING");

        UserDataResponse moderated = service.findByGithubUid("12345");

        assertNull(moderated.basicInfo().nickname());
    }
}
