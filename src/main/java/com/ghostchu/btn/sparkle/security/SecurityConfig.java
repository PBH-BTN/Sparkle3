package com.ghostchu.btn.sparkle.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clients,
            org.springframework.core.env.Environment environment,
            @org.springframework.beans.factory.annotation.Qualifier("stringStringRedisTemplate") org.springframework.data.redis.core.RedisTemplate<String, String> redis,
            org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session> sessions) {
        http.addFilterBefore(new OAuthOriginFilter(redis, clients, environment, sessions),
                org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.class);
        http.csrf(csrf -> csrf
                        .spa()
                        .ignoringRequestMatchers(
                                "/ping/**",
                                "/api/integrations/memberships/**"
                        ))
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/error").permitAll()
                                .requestMatchers("/", "/index.html", "/login", "/register").permitAll()
                                .requestMatchers("/assets/**").permitAll()
                                .requestMatchers("/favicon.ico").permitAll()
                                .requestMatchers("/ping/**").permitAll()
                                .requestMatchers("/login/**").permitAll()
                                .requestMatchers("/oauth2/**").permitAll()
                                .requestMatchers("/oauth/error").permitAll()
                                .requestMatchers("/account-deletion", "/account-deletion/**").permitAll()
                                .requestMatchers("/proxy/**").permitAll()
                                .requestMatchers("/actuator/**").permitAll()
                                .requestMatchers("/api/ui/session").permitAll()
                                .requestMatchers("/api/ui/footer").permitAll()
                                .requestMatchers("/mail-unsubscribe", "/api/ui/mail-unsubscribe").permitAll()
                                .requestMatchers("/api/ui/notices").permitAll()
                                .requestMatchers("/api/integrations/flarum/**").permitAll()
                                .requestMatchers("/api/integrations/memberships/**").permitAll()
                                .requestMatchers("/admin", "/admin/**")
                                .hasAnyAuthority(AdminPolicy.ADMIN_AUTHORITY, AdminPolicy.MODERATOR_AUTHORITY)
                                .requestMatchers("/api/admin/**")
                                .hasAnyAuthority(AdminPolicy.ADMIN_AUTHORITY, AdminPolicy.MODERATOR_AUTHORITY)
                                .requestMatchers("/debug/**").hasAuthority(AdminPolicy.ADMIN_AUTHORITY)
                                .requestMatchers("/css/**", "/js/**", "/img/**").permitAll()
                                .requestMatchers("/api/**").authenticated()
                                .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                writeJsonError(response, 401, "Authentication required");
                                return;
                            }
                            response.sendRedirect("/login");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            handleAccessDenied(request, response, exception);
                        }))
                .headers(headers -> headers
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                        .addHeaderWriter(SecurityConfig::writeDefaultCacheHeaders)
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("frame-ancestors *")))
                .logout(logout -> logout
                        .logoutUrl("/api/ui/logout")
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2AuthenticationSuccessHandler))
                .oauth2Client(Customizer.withDefaults())
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }

    static void handleAccessDenied(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception
    ) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            String message = exception instanceof CsrfException
                    ? "CSRF token is missing or invalid"
                    : "Administrator or moderator access required";
            writeJsonError(response, 403, message);
            return;
        }
        response.sendRedirect("/");
    }

    private static void writeJsonError(
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"data\":null}"
        );
    }

    static void writeDefaultCacheHeaders(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        if (request.getRequestURI().startsWith("/assets/")) {
            return;
        }
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-cache, no-store, max-age=0, must-revalidate"
        );
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
    }
}
