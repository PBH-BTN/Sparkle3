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

    public static Collection<GrantedAuthority> authorities(User user) {
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_USER");
        authorities.add("user");
        if (isAdmin(user)) {
            authorities.add(ADMIN_AUTHORITY);
            authorities.add(ROLE_ADMIN_AUTHORITY);
        }
        if (user != null && user.getRole() != null && !user.getRole().isBlank()) {
            authorities.add(user.getRole());
        }
        return authorities.stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
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
