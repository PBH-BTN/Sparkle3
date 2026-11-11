package com.ghostchu.btn.sparkle.filter;

import com.ghostchu.btn.sparkle.service.FooterMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(3)
@RequiredArgsConstructor
public class FooterRequestMetricsFilter extends OncePerRequestFilter {
    private final FooterMetricsService metricsService;

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/ui/footer");
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            metricsService.recordHttpRequest(request.getRequestURI(), System.nanoTime() - startedAt);
        }
    }
}
