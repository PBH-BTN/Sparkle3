package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AdminPolicy {
    public static final String ADMIN_AUTHORITY = "admin";
    public static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";
    public static final String MODERATOR_AUTHORITY = "moderator";
    public static final String ROLE_MODERATOR_AUTHORITY = "ROLE_MODERATOR";

    private AdminPolicy() {
    }

    public static boolean isAdmin(User user) {
        return user != null
                && user.getBannedAt() == null
                && isAdminRole(user.getRole());
    }

    public static boolean isAdmin(SparkleUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return false;
        }
        if (userDetails.getUser().getBannedAt() != null) {
            return false;
        }
        return isAdmin(userDetails.getUser())
                || hasAdminAuthority(userDetails.getAuthorities());
    }

    public static boolean isModerator(User user) {
        return user != null
                && user.getBannedAt() == null
                && MODERATOR_AUTHORITY.equalsIgnoreCase(
                        user.getRole() == null ? "" : user.getRole().trim()
                );
    }

    /**
     * Administrators inherit every moderator capability.
     */
    public static boolean hasModeratorAccess(User user) {
        return isAdmin(user) || isModerator(user);
    }

    public static Collection<GrantedAuthority> authorities(User user) {
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_USER");
        authorities.add("user");
        if (isAdmin(user)) {
            authorities.add(ADMIN_AUTHORITY);
            authorities.add(ROLE_ADMIN_AUTHORITY);
        }
        if (hasModeratorAccess(user)) {
            authorities.add(MODERATOR_AUTHORITY);
            authorities.add(ROLE_MODERATOR_AUTHORITY);
        }
        if (user != null && user.getRole() != null && !user.getRole().isBlank()) {
            authorities.add(user.getRole());
            authorities.add("ROLE_" + user.getRole().trim().toUpperCase());
        }
        return authorities.stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    public static boolean canAccessModeratorModule(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        if ("/admin".equals(requestUri)) {
            return true;
        }
        return requestUri.startsWith("/admin/tasks")
                || requestUri.startsWith("/admin/announcements")
                || requestUri.startsWith("/admin/userapps")
                || requestUri.startsWith("/admin/github-sync")
                || requestUri.startsWith("/admin/btn")
                || requestUri.startsWith("/admin/modules")
                || requestUri.startsWith("/admin/rules")
                || requestUri.startsWith("/admin/messages")
                || requestUri.startsWith("/api/admin/tasks")
                || requestUri.startsWith("/api/admin/notices")
                || requestUri.startsWith("/api/admin/userapps")
                || requestUri.startsWith("/api/admin/github-sync")
                || requestUri.startsWith("/api/admin/btn")
                || requestUri.startsWith("/api/admin/modules")
                || requestUri.startsWith("/api/admin/rules")
                || requestUri.startsWith("/api/admin/messages");
    }

    private static boolean hasAdminAuthority(Collection<? extends GrantedAuthority> authorities) {
        return authorities != null && authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AdminPolicy::isAdminAuthority);
    }

    private static boolean isAdminAuthority(String authority) {
        return authority != null
                && (ADMIN_AUTHORITY.equalsIgnoreCase(authority)
                || ROLE_ADMIN_AUTHORITY.equalsIgnoreCase(authority));
    }

    private static boolean isAdminRole(String role) {
        return role != null
                && (ADMIN_AUTHORITY.equalsIgnoreCase(role.trim())
                || ROLE_ADMIN_AUTHORITY.equalsIgnoreCase(role.trim()));
    }
}
