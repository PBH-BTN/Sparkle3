package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {
    private final IUserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        boolean allowed = false;
        if (principal instanceof SparkleUserDetails userDetails) {
            var user = userService.getById(userDetails.getUserId());
            boolean admin = AdminPolicy.isAdmin(user);
            boolean moderator = AdminPolicy.isModerator(user);
            allowed = admin
                    || (moderator
                    && AdminPolicy.canAccessModeratorModule(request.getRequestURI()));
            if (!allowed && moderator && !request.getRequestURI().startsWith("/api/")) {
                response.sendRedirect("/admin/tasks");
                return false;
            }
        }
        if (allowed) {
            return true;
        }
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Administrator or moderator access required\",\"data\":null}"
            );
        } else {
            response.sendRedirect("/");
        }
        return false;
    }
}
