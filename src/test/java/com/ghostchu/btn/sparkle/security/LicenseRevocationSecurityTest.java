package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.controller.LicenseRevocationController;
import com.ghostchu.btn.sparkle.controller.admin.AdminLicenseRevocationController;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.PbhLicenseRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LicenseRevocationSecurityTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, LicenseRevocationController.class, AdminLicenseRevocationController.class})
    static class Config implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Bean IUserService users() { return mock(IUserService.class); }
        @Override
        public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
            registry.addInterceptor(new AdminAccessInterceptor(users())).addPathPatterns("/api/admin/**");
        }
        @Bean PbhLicenseRevocationService unsubscribeService() { return mock(PbhLicenseRevocationService.class); }
        @Bean OAuth2AuthenticationSuccessHandler successHandler() { return mock(OAuth2AuthenticationSuccessHandler.class); }
        @Bean(name = "stringStringRedisTemplate") RedisTemplate<String, String> redis() { return mock(RedisTemplate.class); }
        @Bean FindByIndexNameSessionRepository<?> sessions() { return mock(FindByIndexNameSessionRepository.class); }
        @Bean ClientRegistrationRepository clients() {
            ClientRegistration github = ClientRegistration.withRegistrationId("github")
                    .clientId("test").clientSecret("test")
                    .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("https://example.com/login/oauth2/code/github")
                    .authorizationUri("https://example.com/authorize").tokenUri("https://example.com/token")
                    .userInfoUri("https://example.com/user").userNameAttributeName("id").build();
            return new InMemoryClientRegistrationRepository(github);
        }
    }

    @Test
    void publicUnknownLicenseIsNotRevokedAndWritesRequireAdminAndCsrf() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var service = context.getBean(PbhLicenseRevocationService.class);
            String publicPath = "/peerbanhelper/v1/licenses/checkRevoke";
            String adminPath = "/api/admin/memberships/license-revocations";
            mvc.perform(get(publicPath).servletPath(publicPath).param("licenseTo", "a".repeat(64)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.revoked").value(false));
            mvc.perform(get(adminPath).servletPath(adminPath)).andExpect(status().isUnauthorized());
            mvc.perform(get(adminPath).servletPath(adminPath)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user")))
                    .andExpect(status().isForbidden());
            var users = context.getBean(IUserService.class);
            User moderator = new User().setId(2L).setRole("moderator");
            when(users.getById(2L)).thenReturn(moderator);
            mvc.perform(get(adminPath).servletPath(adminPath).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new SparkleUserDetails(moderator, null))))
                    .andExpect(status().isForbidden());
            User admin = new User().setId(1L).setRole("admin");
            when(users.getById(1L)).thenReturn(admin);
            var auth = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new SparkleUserDetails(admin, null));
            String body = "{\"licenseKey\":\"key\",\"reason\":\"reason\"}";
            mvc.perform(post(adminPath).servletPath(adminPath).with(auth).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post(adminPath).servletPath(adminPath).with(auth)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType("application/json").content(body)).andExpect(status().isOk());
            verify(service).revoke(null, "key", "reason", 1L);
        }
    }
}
