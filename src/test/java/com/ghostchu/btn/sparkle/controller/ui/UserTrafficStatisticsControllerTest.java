package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.NetworkTrafficAccess;
import com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.UserTrafficStatisticsService;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
import com.ghostchu.btn.sparkle.service.IUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserTrafficStatisticsControllerTest {
    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void onlyReadsAuthenticatedOwnersFinalResultsAndValidatesPagination() throws Exception {
        var mapper=mock(UserTrafficStatisticsMapper.class);
        var mvc=MockMvcBuilders.standaloneSetup(new UserTrafficStatisticsController(
                        new UserTrafficStatisticsService(mapper), mock(AdminConfigService.class), mock(IUserService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/ui/user/traffic")).andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SparkleUserDetails(new User().setId(7L).setRole("user"),null),null,List.of()));
        when(mapper.count(7)).thenReturn(1000L);
        when(mapper.daily(7,990,30)).thenReturn(List.of(Map.of("day","2020-01-01","upload","9007199254740993")));
        mvc.perform(get("/api/ui/user/traffic?page=34&size=30&uid=999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1000))
                .andExpect(jsonPath("$.records[0].metrics.upload").value("9007199254740993"));
        verify(mapper).daily(7,990,30);
        mvc.perform(get("/api/ui/user/traffic?page=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ui/user/traffic?size=101")).andExpect(status().isBadRequest());
    }

    @Test
    void networkRequiresAuthenticationAndUsesTheCurrentUsersAllowedMetrics() throws Exception {
        var mapper = mock(UserTrafficStatisticsMapper.class);
        var config = mock(AdminConfigService.class);
        var users = mock(IUserService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserTrafficStatisticsController(
                        new UserTrafficStatisticsService(mapper), config, users))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        var request = get("/api/ui/user/traffic/network?totalLoss=true");
        mvc.perform(request).andExpect(status().isUnauthorized());

        var current = new User().setId(7L).setRole("user");
        when(users.getById(7L)).thenReturn(current);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SparkleUserDetails(new User().setId(7L).setRole("moderator"), null), null, List.of()));
        when(config.homepageTrafficAccess(current)).thenReturn(new NetworkTrafficAccess(false, false));
        mvc.perform(request).andExpect(status().isForbidden());
        verifyNoInteractions(mapper);

        when(config.homepageTrafficAccess(current)).thenReturn(new NetworkTrafficAccess(true, false));
        when(mapper.network(any(), any(), eq(true), eq(false)))
                .thenReturn(List.of(Map.of("day", "2026-01-01", "averageLoss", "10")));
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metrics.averageLoss").value("10"))
                .andExpect(jsonPath("$[0].metrics.totalLoss").doesNotExist());
    }
}
