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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(config.homepageTrafficAccess(current)).thenReturn(new NetworkTrafficAccess(false, false, false, false, false));
        mvc.perform(request).andExpect(status().isForbidden());
        verifyNoInteractions(mapper);

        var access = new NetworkTrafficAccess(false, false, false, true, false);
        when(config.homepageTrafficAccess(current)).thenReturn(access);
        when(mapper.network(any(), any(), eq(access)))
                .thenReturn(List.of(Map.of("day", "2026-01-01", "averageLoss", "10")));
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metrics.averageLoss").value("10"))
                .andExpect(jsonPath("$[0].metrics.totalLoss").doesNotExist());
    }

    @Test
    void networkReturnsPlotValuesForTheRequestedRange() throws Exception {
        var mapper = mock(UserTrafficStatisticsMapper.class);
        var config = mock(AdminConfigService.class);
        var users = mock(IUserService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserTrafficStatisticsController(
                        new UserTrafficStatisticsService(mapper), config, users))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        var user = new User().setId(7L).setRole("user");
        var access = new NetworkTrafficAccess(true, false, true, false, true);
        when(users.getById(7L)).thenReturn(user);
        when(config.homepageTrafficAccess(user)).thenReturn(access);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SparkleUserDetails(user, null), null, List.of()));
        when(mapper.network(any(), any(), eq(access))).thenAnswer(invocation -> {
            LocalDate from = invocation.getArgument(0);
            LocalDate through = invocation.getArgument(1);
            assertEquals(through.minusDays(6), from);
            return List.of(
                    Map.of("day", from.toString(), "upload", "9007199254740993", "seeding", "12.5", "totalLoss", "10"),
                    Map.of("day", through.toString(), "upload", "2", "seeding", "0.25", "totalLoss", "20"));
        });
        mvc.perform(get("/api/ui/user/traffic/network?days=8")).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
        mvc.perform(get("/api/ui/user/traffic/network?days=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].metrics.upload").value("9007199254740995"))
                .andExpect(jsonPath("$[1].metrics.seeding").value("12.75"))
                .andExpect(jsonPath("$[0].metrics.totalLoss").value("10"))
                .andExpect(jsonPath("$[1].metrics.totalLoss").value("20"))
                .andExpect(jsonPath("$[1].metrics.uploadAck").doesNotExist());
    }
}
