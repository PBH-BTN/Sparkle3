package com.ghostchu.btn.sparkle.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/** Browser-bound, one-use handoff between origins sharing this application. */
public class OAuthOriginFilter extends OncePerRequestFilter {
    private static final String FLOW = OAuthOriginFilter.class.getName();
    private static final String PREFIX = "sparkle:oauth-origin:";
    private final RedisTemplate<String, String> redis;
    private final ClientRegistrationRepository clients;
    private final org.springframework.core.env.Environment environment;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public OAuthOriginFilter(@Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redis,
                             ClientRegistrationRepository clients, org.springframework.core.env.Environment environment,
                             FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.redis = redis;
        this.environment = environment;
        this.clients = clients;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String configuredCallback = clients.findByRegistrationId("github").getRedirectUri();
        if (!environment.getProperty("sparkle.web.allow-alternate-domains", Boolean.class, true)
                && !path.startsWith("/api/") && !path.startsWith("/ping/")
                && !configuredCallback.contains("{")) {
            String primary = URI.create(configuredCallback).resolve("/").toString();
            String current = URI.create(request.getRequestURL().toString()).resolve("/").toString();
            if (!primary.equals(current)) {
                response.sendRedirect(primary);
                return;
            }
        }
        if (!path.startsWith("/oauth2/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Referrer-Policy", "no-referrer");
        String origin = URI.create(request.getRequestURL().toString()).resolve("/").toString();
        String callback = clients.findByRegistrationId("github").getRedirectUri();
        if (path.equals("/oauth2/authorization/github") && !callback.contains("{")) {
            String canonical = URI.create(callback).resolve("/").toString();
            if (!origin.equals(canonical)) {
                String nonce = UUID.randomUUID().toString();
                request.getSession().setAttribute(FLOW, nonce);
                String ticket = issue(origin + "\n" + nonce + "\n" + request.getSession().getId());
                response.sendRedirect(canonical + "oauth2/relay/start?ticket=" + ticket);
                return;
            }
        }
        if (path.equals("/oauth2/relay/start")) {
            String value = consume(request);
            if (value == null) { response.sendError(400); return; }
            String[] start = value.split("\\n", 3);
            Session initiating = start.length == 3 ? sessions.findById(start[2]) : null;
            if (initiating == null) { response.sendError(400); return; }
            Object intent = initiating.getAttribute(com.ghostchu.btn.sparkle.service.account.UserAccountDeletionService.REAUTH_SESSION_ATTRIBUTE);
            if (intent != null) request.getSession().setAttribute(com.ghostchu.btn.sparkle.service.account.UserAccountDeletionService.REAUTH_SESSION_ATTRIBUTE, intent);
            request.getSession().setAttribute(FLOW, start[0] + "\n" + start[1]);
            response.sendRedirect("/oauth2/authorization/github");
            return;
        }
        if (path.equals("/oauth2/relay/export")) {
            HttpSession session = request.getSession(false);
            String value = session == null ? null : (String) session.getAttribute(FLOW);
            if (value == null || !value.contains("\n")) { response.sendError(400); return; }
            session.removeAttribute(FLOW);
            String[] parts = value.split("\n", 2);
            String ticket = issue(parts[1] + "\n" + session.getId());
            response.sendRedirect(parts[0] + "oauth2/relay/finish?ticket=" + ticket);
            return;
        }
        if (path.equals("/oauth2/relay/finish")) {
            String value = consume(request);
            HttpSession local = request.getSession(false);
            if (value == null || local == null) { response.sendError(400); return; }
            String[] parts = value.split("\n", 2);
            if (parts.length != 2 || !parts[0].equals(local.getAttribute(FLOW))) {
                response.sendError(400); return;
            }
            Session source = sessions.findById(parts[1]);
            if (source == null) { response.sendError(400); return; }
            local.removeAttribute(FLOW);
            request.changeSessionId();
            for (String key : new String[]{"SPRING_SECURITY_CONTEXT", PendingGithubRegistration.SESSION_ATTRIBUTE}) {
                Object attribute = source.getAttribute(key);
                if (attribute != null) local.setAttribute(key, attribute);
            }
            response.sendRedirect(source.getAttribute(PendingGithubRegistration.SESSION_ATTRIBUTE) == null
                    ? "/" : "/register");
            return;
        }
        chain.doFilter(request, response);
    }

    public static String successTarget(HttpServletRequest request, String fallback) {
        Object flow = request.getSession().getAttribute(FLOW);
        return flow instanceof String value && value.contains("\n") ? "/oauth2/relay/export" : fallback;
    }

    private String issue(String value) {
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + ticket, value, Duration.ofMinutes(2));
        return ticket;
    }

    private String consume(HttpServletRequest request) {
        String ticket = request.getParameter("ticket");
        if (ticket == null || !ticket.matches("[a-f0-9-]{36}")) return null;
        return redis.opsForValue().getAndDelete(PREFIX + ticket);
    }
}
