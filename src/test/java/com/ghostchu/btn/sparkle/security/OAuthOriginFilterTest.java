package com.ghostchu.btn.sparkle.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuthOriginFilterTest {
    @Test
    void preservesLocalLoginAndRelaysOnlyServerSideFlow() {
        var request = new MockHttpServletRequest();
        assertEquals("/register", OAuthOriginFilter.successTarget(request, "/register"));
        request.getSession().setAttribute(OAuthOriginFilter.class.getName(), "https://example.org/\nnonce");
        assertEquals("/oauth2/relay/export", OAuthOriginFilter.successTarget(request, "/"));
    }
    @Test
    void missingTicketIsRejected() throws Exception {
        var clients = org.mockito.Mockito.mock(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class);
        var client = org.springframework.security.oauth2.client.registration.ClientRegistration.withRegistrationId("github")
                .clientId("test").authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://primary.example/login/oauth2/code/github")
                .authorizationUri("https://github.com/login/oauth/authorize").tokenUri("https://github.com/login/oauth/access_token")
                .build();
        org.mockito.Mockito.when(clients.findByRegistrationId("github")).thenReturn(client);
        var filter = new OAuthOriginFilter(null, clients, new org.springframework.mock.env.MockEnvironment(), null);
        var request = new MockHttpServletRequest("GET", "/oauth2/relay/finish");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());
        assertEquals(400, response.getStatus());
    }}
