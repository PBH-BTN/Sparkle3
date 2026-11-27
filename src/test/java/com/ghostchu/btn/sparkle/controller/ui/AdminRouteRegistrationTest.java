package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.AdminAccessInterceptor;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminRouteRegistrationTest {
    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyNavigationEntryServesTheSpaAndMatchesModeratorAuthorization() throws Exception {
        var source = Files.readString(Path.of("frontend/src/router/adminNavigation.ts"));
        var entries = Pattern.compile("'path': '(/admin/[^']+)'[\\s\\S]*?'moderator': (true|false)").matcher(source);
        var users = mock(IUserService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SpaController()).addInterceptors(new AdminAccessInterceptor(users)).build();
        int count = 0;
        while (entries.find()) {
            String path = entries.group(1);
            boolean moderator = Boolean.parseBoolean(entries.group(2));
            assertEquals(moderator, AdminPolicy.canAccessModeratorModule(path), path);
            for (String role : new String[]{"admin", "moderator"}) {
                var user = new User().setId(12L).setRole(role);
                when(users.getById(12L)).thenReturn(user);
                var principal = new SparkleUserDetails(user, null);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
                var result = mvc.perform(get(path));
                if (role.equals("admin") || moderator) result.andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
                else result.andExpect(status().isFound()).andExpect(redirectedUrl("/admin/tasks"));
            }
            count++;
        }
        assertTrue(count >= 26, "No admin route inventory was checked");
    }
}
