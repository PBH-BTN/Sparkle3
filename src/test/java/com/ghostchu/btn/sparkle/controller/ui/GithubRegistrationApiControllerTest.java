package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.PendingGithubRegistration;
import com.ghostchu.btn.sparkle.service.BlurredAvatarDiskCache;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubRegistrationApiControllerTest {
    @Test
    void oneClickRegistrationCreatesUserAndClearsPendingState() {
        IUserService userService = mock(IUserService.class);
        BlurredAvatarDiskCache avatarCache = mock(BlurredAvatarDiskCache.class);
        PointsService pointsService = mock(PointsService.class);
        var controller = new GithubRegistrationApiController(
                userService,
                avatarCache,
                pointsService
        );
        var authentication = authentication(42L);
        var session = new MockHttpSession();
        session.setAttribute(
                PendingGithubRegistration.SESSION_ATTRIBUTE,
                registration(42L)
        );
        var request = new MockHttpServletRequest();
        request.setSession(session);
        request.setRemoteAddr("127.0.0.1");

        User user = new User()
                .setId(7L)
                .setEmail("user@example.com")
                .setNickname("Test User");
        when(userService.userGithubOAuthLogin(
                eq(authentication),
                any(InetAddress.class),
                eq(42L),
                eq("test-user"),
                eq("https://example.com/avatar.png"),
                eq("Test User"),
                eq("user@example.com")
        )).thenReturn(user);

        var result = controller.register(authentication, request);

        assertEquals("7", result.userId());
        assertNull(session.getAttribute(PendingGithubRegistration.SESSION_ATTRIBUTE));
        verify(avatarCache).refreshOnLogin(7L);
        verify(pointsService).awardDailyLogin(7L);
    }

    @Test
    void rejectsRegistrationWhenAuthorizedGithubAccountChanged() {
        var controller = new GithubRegistrationApiController(
                mock(IUserService.class),
                mock(BlurredAvatarDiskCache.class),
                mock(PointsService.class)
        );
        var session = new MockHttpSession();
        session.setAttribute(
                PendingGithubRegistration.SESSION_ATTRIBUTE,
                registration(99L)
        );

        assertThrows(
                ResponseStatusException.class,
                () -> controller.pending(authentication(42L), session)
        );
        assertNull(session.getAttribute(PendingGithubRegistration.SESSION_ATTRIBUTE));
    }

    private OAuth2AuthenticationToken authentication(long githubUid) {
        var oauth2User = new DefaultOAuth2User(
                AuthorityUtils.NO_AUTHORITIES,
                Map.of("id", githubUid),
                "id"
        );
        return new OAuth2AuthenticationToken(
                oauth2User,
                AuthorityUtils.NO_AUTHORITIES,
                "github"
        );
    }

    private PendingGithubRegistration registration(long githubUid) {
        return new PendingGithubRegistration(
                githubUid,
                "test-user",
                "https://example.com/avatar.png",
                "Test User",
                "user@example.com"
        );
    }
}
