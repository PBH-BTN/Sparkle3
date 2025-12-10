package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filter to handle BitTorrent tracker requests with invalid parameter encoding.
 * This filter runs before Spring Security to prevent encoding errors from being logged.
 */
@Component
@Order(1) // Run before Spring Security
@Slf4j
public class TrackerRequestFilter extends OncePerRequestFilter {

    private static final String TRACKER_RESPONSE = "d14:failure reason23:This is not a tracker.e15:retry in nevere";

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // Check if this is a tracker announce request
        if (requestURI != null && (requestURI.equals("/announce") || requestURI.equals("/tracker/announce"))) {
            // Check for info_hash in query string (raw, before parameter parsing)
            String queryString = request.getQueryString();

            if (queryString != null && queryString.contains("info_hash")) {
                // This is likely a BitTorrent tracker request with binary data
                // Handle it directly without letting Spring parse parameters
                log.debug("Intercepted BitTorrent tracker request to {}, bypassing parameter parsing", requestURI);

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(TRACKER_RESPONSE);
                response.getWriter().flush();

                // Don't continue the filter chain
                return;
            }
        }

        // Continue with normal request processing
        filterChain.doFilter(request, response);
    }
}
