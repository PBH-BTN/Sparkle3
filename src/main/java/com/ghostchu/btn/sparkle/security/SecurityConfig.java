package com.ghostchu.btn.sparkle.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/error").permitAll()
                                .requestMatchers("/assets/**").permitAll()
                                .requestMatchers("/favicon.ico").permitAll()
                                .requestMatchers("/ping/**").permitAll()
                                .requestMatchers("/login/**").permitAll()
                                .requestMatchers("/proxy/**").permitAll()
                                .requestMatchers("/announce").permitAll()
                                .requestMatchers("/tracker/announce").permitAll()
                                .requestMatchers("/actuator/**").permitAll()
                                .requestMatchers("/admin/**").hasAnyRole("ADMIN")
                                .requestMatchers("/css/**", "/js/**", "/img/**").permitAll()
//                                .requestMatchers("/banhistory/**").authenticated()
//                                .requestMatchers("/api/banhistory/**").authenticated()
//                                .requestMatchers("/swarm-tracker/**").authenticated()
//                                .requestMatchers("/api/swarmtracker/**").authenticated()
//                                .requestMatchers("/client-discovery/**").authenticated()
//                                .requestMatchers("/api/clientdiscovery/**").authenticated()
                                .anyRequest().authenticated())
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("frame-ancestors *")))
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2AuthenticationSuccessHandler))
                .oauth2Client(Customizer.withDefaults())
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }
}