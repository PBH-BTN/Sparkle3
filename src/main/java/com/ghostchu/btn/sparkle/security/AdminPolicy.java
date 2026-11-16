package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
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
                && !user.isSuspended()
                && user.roleNames().contains(ADMIN_AUTHORITY);
    }

    public static boolean isModerator(User user) {
        return user != null
                && !user.isSuspended()
                && user.roleNames().contains(MODERATOR_AUTHORITY);
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
        if (user != null && !user.isSuspended()) {
            for (String role : user.roleNames()) {
                authorities.add(role);
                authorities.add("ROLE_" + role.toUpperCase(Locale.ROOT));
            }
            if (isAdmin(user)) {
                authorities.add(MODERATOR_AUTHORITY);
                authorities.add(ROLE_MODERATOR_AUTHORITY);
            }
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

}
