package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GzipDecompressionFilter extends OncePerRequestFilter {
    @Autowired
    private BtnRuntimeConfigService btnConfigService;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {
        if (request.getHeader("Content-Encoding") != null && request.getHeader("Content-Encoding").contains("gzip")) {
            request = new GzipHttpServletRequestWrapper(
                    request,
                    btnConfigService.longValue("security", "zipBombThreshold")
            );
        }
        filterChain.doFilter(request, response);
    }
}
