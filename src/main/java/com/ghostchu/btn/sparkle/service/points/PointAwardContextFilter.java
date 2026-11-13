package com.ghostchu.btn.sparkle.service.points;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class PointAwardContextFilter extends OncePerRequestFilter {
    private final PointAwardContext context;
    private final PointsService pointsService;

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/ping/");
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {
        context.begin(eventType(request.getRequestURI()));
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            PointAwardBucket bucket = context.finish();
            if (failure == null
                    && response.getStatus() >= 200
                    && response.getStatus() < 400
                    && bucket != null
                    && bucket.hasWork()) {
                try {
                    pointsService.commit(bucket);
                } catch (Exception exception) {
                    log.error(
                            "Unable to commit points event {} for user {}",
                            bucket.getEventId(),
                            bucket.getUserId(),
                            exception
                    );
                }
            }
        }
    }

    static String eventType(String uri) {
        return switch (uri) {
            case "/ping/syncBanHistory" -> "BAN_HISTORY_SYNC";
            case "/ping/syncSwarm" -> "SWARM_SYNC";
            case "/ping/heartbeat" -> "KEEPALIVE";
            case "/ping/ruleIpDenylist" -> "RULES_FETCH";
            case "/ping/ruleIpAllowlist" -> "RULES_FETCH";
            case "/ping/rulePeerIdentity" -> "RULES_FETCH";
            case "/ping/queryIp" -> "IP_QUERY";
            case "/ping/queryIp/widget/data" -> "IP_QUERY";
            case "/ping/config" -> "CONFIG_FETCH";
            default -> "PING_REQUEST";
        };
    }
}
