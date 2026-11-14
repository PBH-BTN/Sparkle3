package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.UserTrafficStatisticsService;
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
        var mvc=MockMvcBuilders.standaloneSetup(new UserTrafficStatisticsController(new UserTrafficStatisticsService(mapper)))
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
}
