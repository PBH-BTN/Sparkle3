package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.handler.GlobalExceptionHandler;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.AdminAccessInterceptor;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
import com.ghostchu.btn.sparkle.service.AdminSettingsService;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import com.ghostchu.btn.sparkle.service.IUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminSettingsControllerTest {
    private final Map<String, String> values = new HashMap<>();
    private final DatabaseConfigService database = mock(DatabaseConfigService.class);
    private final IUserService users = mock(IUserService.class);
    private final MockMvc mvc;

    AdminSettingsControllerTest() throws Exception {
        when(database.getRawValues(anyList())).thenAnswer(call -> {
            Map<String, String> selected = new HashMap<>();
            for (String key : call.<List<String>>getArgument(0)) if (values.containsKey(key)) selected.put(key, values.get(key));
            return selected;
        });
        when(database.update(anyString(), anyString(), any())).thenAnswer(call -> {
            values.put(call.getArgument(0), call.getArgument(1)); return null;
        });
        var runtime = new AdminConfigService(database, 20, 20, false, false, true, 60, 7,
                true, false, true, "{{title}}", "{{content}}");
        mvc = MockMvcBuilders.standaloneSetup(new AdminSettingsController(new AdminSettingsService(runtime, database)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addInterceptors(new AdminAccessInterceptor(users)).build();
        authenticate("admin");
    }

    private void authenticate(String role) {
        var user = new User().setId(12L).setRole(role);
        when(users.getById(12L)).thenReturn(user);
        var principal = new SparkleUserDetails(user, null);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopedFormsSaveReloadAndPreserveEachOthersValues() throws Exception {
        mvc.perform(put("/api/admin/settings/statistics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"refreshMinutes\":30,\"windowDays\":14}"))
                .andExpect(status().isOk()).andExpect(jsonPath("windowDays").value(14));
        mvc.perform(put("/api/admin/settings/features").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientDiscoveryEnabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/settings/contact-template").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Test {{title}}\",\"body\":\"Test {{content}}\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/settings/homepage-display").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reportedStaffOnly\":true,\"confirmedStaffOnly\":false,\"activityStaffOnly\":true,\"averageLossStaffOnly\":false,\"totalLossStaffOnly\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/settings/statistics")).andExpect(status().isOk()).andExpect(jsonPath("refreshMinutes").value(30));
        mvc.perform(get("/api/admin/settings/features")).andExpect(jsonPath("clientDiscoveryEnabled").value(true));
        mvc.perform(get("/api/admin/settings/contact-template")).andExpect(jsonPath("subject").value("Test {{title}}"));
        mvc.perform(get("/api/admin/settings/homepage-display")).andExpect(jsonPath("enabled").value(false));
        verify(database, never()).update(startsWith("sparkle.ping."), anyString(), any());
    }

    @Test
    void invalidAndUnauthorizedRequestsCannotSave() throws Exception {
        mvc.perform(put("/api/admin/settings/statistics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"refreshMinutes\":0,\"windowDays\":7}"))
                .andExpect(status().isBadRequest());
        authenticate("moderator");
        for (String section : List.of("features", "statistics", "homepage-display", "contact-template")) {
            mvc.perform(get("/api/admin/settings/" + section)).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/settings/" + section).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verify(database, never()).update(anyString(), anyString(), any());
    }
}
