package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GzipDecompressionFilter extends OncePerRequestFilter {

    @Value("${sparkle.security.zip-bomb-threshold}")
    private long zipBombThreshold;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {
        if (request.getHeader("Content-Encoding") != null && request.getHeader("Content-Encoding").contains("gzip")) {
            request = new GzipHttpServletRequestWrapper(request, zipBombThreshold);
        }
        filterChain.doFilter(request, response);
    }
}
