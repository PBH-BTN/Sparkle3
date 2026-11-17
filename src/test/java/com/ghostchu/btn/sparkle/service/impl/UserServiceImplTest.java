package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.mapper.UserRelMapper;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.moderation.ContentModerationService;
import com.ghostchu.btn.sparkle.service.moderation.UserProfileChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceImplTest {
    private static final String AVATAR = "https://avatars.githubusercontent.com/u/42?v=4";
    private final UserMapper users = mock(UserMapper.class);
    private final UserRelMapper relations = mock(UserRelMapper.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserServiceImpl service = new UserServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", users);
        ReflectionTestUtils.setField(service, "userRelMapper", relations);
        ReflectionTestUtils.setField(service, "userPrivacyService", mock(UserPrivacyService.class));
        ReflectionTestUtils.setField(service, "eventPublisher", events);
        ReflectionTestUtils.setField(service, "contentModeration", new ContentModerationService(mock()));
    }

    @Test
    void registrationRequestsAvatarReviewAfterSavingTheAccountAndGithubBinding() {
        when(users.insert(any(User.class))).thenAnswer(call -> {
            call.<User>getArgument(0).setId(7L);
            return 1;
        });
        when(relations.insert(any(UserRel.class))).thenReturn(1);

        User user = login();

        assertEquals("PENDING", user.getAvatarModerationStatus());
        var order = inOrder(users, relations, events);
        order.verify(users).insert(user);
        order.verify(relations).insert(any(UserRel.class));
        order.verify(events).publishEvent(new UserProfileChangedEvent(
                7L, 0L, 1L, false, true, "Example", AVATAR));
    }

    @Test
    void loginRequestsReviewForTheChangedAvatarEvenWhenTheNicknameIsUnchanged() {
        User user = existingUser("https://avatars.githubusercontent.com/u/42?v=3");

        login();

        assertEquals(AVATAR, user.getAvatar());
        assertEquals("PENDING", user.getAvatarModerationStatus());
        assertEquals(4L, user.getAvatarReviewGeneration());
        var order = inOrder(users, events);
        order.verify(users).updateById(user);
        order.verify(events).publishEvent(new UserProfileChangedEvent(
                7L, 0L, 4L, false, true, "Example", AVATAR));
    }

    @Test
    void loginDoesNotRequestAnotherReviewIfTheAvatarUrlIsUnchanged() {
        User user = existingUser(AVATAR);

        login();

        assertEquals("REJECTED", user.getAvatarModerationStatus());
        assertEquals(3L, user.getAvatarReviewGeneration());
        verifyNoInteractions(events);
    }

    private User existingUser(String avatar) {
        User user = new User().setId(7L).setNickname("Example").setAvatar(avatar)
                .setAvatarModerationStatus("REJECTED").setAvatarReviewGeneration(3L);
        when(relations.findUserRelByPlatformUserId("github", "42"))
                .thenReturn(new UserRel().setBindUserId(7L));
        when(users.selectForUpdate(7L)).thenReturn(user);
        return user;
    }

    private User login() {
        return service.userGithubOAuthLogin(mock(Authentication.class),
                InetAddress.getLoopbackAddress(), 42L, "example", AVATAR, "Example", "example@example.com");
    }
}
