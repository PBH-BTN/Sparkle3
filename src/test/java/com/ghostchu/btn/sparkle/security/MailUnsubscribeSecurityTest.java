package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.controller.ui.MailUnsubscribeController;
import com.ghostchu.btn.sparkle.service.MailUnsubscribeService;
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

class MailUnsubscribeSecurityTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, MailUnsubscribeController.class})
    static class Config {
        @Bean MailUnsubscribeService unsubscribeService() { return mock(MailUnsubscribeService.class); }
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
    void permitsAnonymousCredentialRequestsButStillRequiresCsrfForWrites() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            MailUnsubscribeService service = context.getBean(MailUnsubscribeService.class);
            when(service.options("credential")).thenReturn(List.of());
            var response = mvc.perform(get("/api/ui/mail-unsubscribe").servletPath("/api/ui/mail-unsubscribe").header("X-Unsubscribe-Token", "credential"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var csrfCookie = response.getCookie("XSRF-TOKEN");
            org.junit.jupiter.api.Assertions.assertNotNull(csrfCookie);
            mvc.perform(post("/api/ui/mail-unsubscribe").servletPath("/api/ui/mail-unsubscribe").header("X-Unsubscribe-Token", "credential")
                    .contentType("application/json").content("{\"categories\":[\"DIRECT_MESSAGE\"]}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/ui/mail-unsubscribe").servletPath("/api/ui/mail-unsubscribe").cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .header("X-Unsubscribe-Token", "credential").contentType("application/json")
                    .content("{\"categories\":[\"DIRECT_MESSAGE\"]}"))
                    .andExpect(status().isOk());
            verify(service).unsubscribe("credential", List.of("DIRECT_MESSAGE"));
            mvc.perform(get("/api/ui/notifications").servletPath("/api/ui/notifications")).andExpect(status().isUnauthorized());
        }
    }
}
