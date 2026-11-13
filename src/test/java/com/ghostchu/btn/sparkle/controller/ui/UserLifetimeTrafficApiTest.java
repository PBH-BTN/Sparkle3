package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.constants.*;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.LifetimeTraffic;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserLifetimeTrafficApiTest {
    private final IUserService users = mock(IUserService.class);
    private final UserPrivacyService privacy = mock(UserPrivacyService.class);
    private final UserLifetimeTrafficMapper traffic = mock(UserLifetimeTrafficMapper.class);
    private final User user = new User().setId(1L).setRole("user");
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserApiController(
                    users, traffic, new UserPublicProfileService(users, traffic, privacy),
                    mock(UserContactEmailService.class), privacy, mock(UserMessageService.class)))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();

    @AfterEach
    @BeforeEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new SparkleUserDetails(user, null), null,
                        java.util.List.of()));
        when(users.getById(1L)).thenReturn(user);
        when(privacy.get(1L)).thenReturn(UserPrivacySettings.defaults(1L));
    }

    @Test
    void returnsExactDecimalStringsAndRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/ui/user/profile")).andExpect(status().isUnauthorized());
        login();
        when(traffic.selectByUserId(1L)).thenReturn(new LifetimeTraffic(
                "9223372036854775808", "0", "0", "0", null, true, null));
        mvc.perform(get("/api/ui/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifetimeTraffic.sentTrafficSelfReport").value("9223372036854775808"))
                .andExpect(jsonPath("$.user.privacy.lifetimeTrafficVisibility").value("HIDDEN"));
    }

    @Test
    void acceptsTheNewPrivacyOptionAndRejectsUnknownValues() throws Exception {
        login();
        mvc.perform(put("/api/ui/user/privacy").contentType("application/json").content("""
                        {"avatarVisibility":"BLURRED","usernameVisibility":"BLURRED",
                         "pointsVisibility":"SHOW","lifetimeTrafficVisibility":"SHOW"}
                        """))
                .andExpect(status().isOk());
        verify(privacy).update(1L, AvatarVisibility.BLURRED, UsernameVisibility.BLURRED,
                PointsVisibility.SHOW, LifetimeTrafficVisibility.SHOW);
        mvc.perform(put("/api/ui/user/privacy").contentType("application/json").content("""
                        {"avatarVisibility":"BLURRED","usernameVisibility":"BLURRED",
                         "pointsVisibility":"SHOW","lifetimeTrafficVisibility":"INVALID"}
                        """))
                .andExpect(status().isBadRequest());
    }
}
